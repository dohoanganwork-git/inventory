package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.songdem.inventory.domain.Enums.MovementType;
import vn.songdem.inventory.domain.Enums.NotificationType;
import vn.songdem.inventory.domain.Item;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.StockMovement;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.repository.StockMovementRepository;

import java.time.LocalDate;
import java.util.*;

/** Mọi thay đổi tồn kho đi qua lớp này để ghi thẻ kho và kiểm tra cảnh báo. */
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final StockMovementRepository movementRepository;
    private final NotificationService notificationService;

    /** Ngữ cảnh một nghiệp vụ: gom các biến động để kiểm tra cảnh báo một lần. */
    public static class StockBatch {
        private final Map<String, Integer> firstBefore = new LinkedHashMap<>();
        private final Map<String, Item> items = new LinkedHashMap<>();
    }

    public StockBatch batch() {
        return new StockBatch();
    }

    /**
     * Thay đổi tồn kho của mặt hàng.
     * @param clampAtZero true: tồn không xuống dưới 0 (trả về lượng thực trừ)
     * @return lượng thay đổi thực tế
     */
    public int change(StockBatch batch, Item item, int delta, MovementType type, LocalDate date,
                      String refType, String refId, String refCode, String note, User user, boolean clampAtZero) {
        int before = item.getCurrentStock();
        int after = before + delta;
        if (after < 0) {
            if (!clampAtZero) {
                throw vn.songdem.inventory.web.ApiException.conflict("NEGATIVE_STOCK",
                        "Thao tác làm tồn kho của \"" + item.getName() + "\" bị âm (tồn hiện tại " + before + ")");
            }
            after = 0;
        }
        int applied = after - before;
        batch.firstBefore.putIfAbsent(item.getId(), before);
        batch.items.put(item.getId(), item);
        if (applied == 0) return 0;
        item.setCurrentStock(after);
        StockMovement m = new StockMovement();
        m.setItem(item);
        m.setType(type);
        m.setQuantityChange(applied);
        m.setStockBefore(before);
        m.setStockAfter(after);
        m.setMovementDate(date);
        m.setRefType(refType);
        m.setRefId(refId);
        m.setRefCode(refCode);
        m.setNote(note);
        m.setCreatedBy(user);
        movementRepository.save(m);
        return applied;
    }

    /** Kiểm tra mốc tồn thấp sau nghiệp vụ và gửi cảnh báo cho Quản lý, Nhân viên. */
    public List<Item> finish(StockBatch batch, String source) {
        List<Item> crossed = new ArrayList<>();
        for (var e : batch.items.entrySet()) {
            Item it = e.getValue();
            int before = batch.firstBefore.get(e.getKey());
            if (it.isActive() && it.getCurrentStock() <= it.getMinStock() && before > it.getMinStock()) {
                crossed.add(it);
            }
        }
        if (!crossed.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Item it : crossed) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(it.getName()).append(": còn ").append(it.getCurrentStock()).append(" ").append(it.getUnit())
                  .append(" (mốc ").append(it.getMinStock()).append(")");
            }
            notificationService.notifyRoles(EnumSet.of(Role.MANAGER, Role.STAFF), NotificationType.LOW_STOCK,
                    crossed.size() + " mặt hàng sắp hết sau " + source, sb.toString(), "/canh-bao");
        }
        return crossed;
    }
}
