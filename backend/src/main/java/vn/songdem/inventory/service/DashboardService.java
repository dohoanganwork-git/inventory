package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.domain.Enums.StocktakeStatus;
import vn.songdem.inventory.dto.CatalogDtos.ItemDto;
import vn.songdem.inventory.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ItemRepository itemRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final SalesExportRepository salesRepository;
    private final SalesExportLineRepository salesLineRepository;
    private final StocktakeRepository stocktakeRepository;
    private final DebtReconciliationRepository debtReconRepository;
    private final CatalogService catalogService;

    public record DailyPoint(LocalDate date, int quantity, BigDecimal revenue) {}
    public record TopItem(String code, String name, String unit, int quantity, BigDecimal revenue) {}
    public record DashboardDto(int itemCount, int lowStockCount, int outOfStockCount, int overStockCount,
                               BigDecimal stockValue, BigDecimal openDebt, BigDecimal overdueDebt, int openDebtReceipts,
                               int pendingStocktakes, int recountStocktakes, int openDebtRecons,
                               LocalDate latestSalesDate, boolean salesUpToDate,
                               List<DailyPoint> sales14, List<TopItem> topItems, List<ItemDto> lowStockItems) {}

    @Transactional(readOnly = true)
    public DashboardDto get(Role role) {
        boolean values = role != Role.STAFF;
        List<Item> items = itemRepository.findAllWithRefs().stream().filter(Item::isActive).toList();
        int low = (int) items.stream().filter(i -> "LOW".equals(i.stockStatus())).count();
        int out = (int) items.stream().filter(i -> i.getCurrentStock() == 0).count();
        int over = (int) items.stream().filter(i -> "OVER".equals(i.stockStatus())).count();
        BigDecimal value = items.stream().map(i -> (i.getLastCost() == null ? BigDecimal.ZERO : i.getLastCost())
                .multiply(BigDecimal.valueOf(i.getCurrentStock()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debt = BigDecimal.ZERO, overdue = BigDecimal.ZERO;
        int openReceipts = 0;
        LocalDate today = LocalDate.now();
        if (values) {
            for (GoodsReceipt r : receiptRepository.findOpenDebts()) {
                debt = debt.add(r.getRemainingAmount());
                openReceipts++;
                if (r.getDueDate() != null && r.getDueDate().isBefore(today)) overdue = overdue.add(r.getRemainingAmount());
            }
        }
        int pending = (int) stocktakeRepository.countByStatus(StocktakeStatus.PENDING_REVIEW);
        int recount = (int) stocktakeRepository.countByStatus(StocktakeStatus.RECOUNT_REQUESTED);
        int openRecons = (int) debtReconRepository.findAll().stream().filter(d -> d.getStatus() == Enums.DebtReconStatus.IN_PROGRESS).count();
        Optional<LocalDate> latest = salesRepository.findLatestSalesDate();
        boolean upToDate = latest.isPresent() && !latest.get().isBefore(today.minusDays(1));

        LocalDate from = today.minusDays(13);
        Map<LocalDate, int[]> q = new TreeMap<>();
        Map<LocalDate, BigDecimal> rev = new TreeMap<>();
        Map<String, TopItem> top = new HashMap<>();
        LocalDate from30 = today.minusDays(29);
        for (SalesExportLine l : salesLineRepository.findActiveInPeriod(from30, today)) {
            LocalDate d = l.getSalesExport().getSalesDate();
            BigDecimal amount = l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity()));
            if (!d.isBefore(from)) {
                q.computeIfAbsent(d, k -> new int[1])[0] += l.getQuantity();
                rev.merge(d, amount, BigDecimal::add);
            }
            Item it = l.getItem();
            top.merge(it.getId(), new TopItem(it.getCode(), it.getName(), it.getUnit(), l.getQuantity(), amount),
                    (a, b) -> new TopItem(a.code(), a.name(), a.unit(), a.quantity() + b.quantity(), a.revenue().add(b.revenue())));
        }
        List<DailyPoint> series = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            series.add(new DailyPoint(d, q.containsKey(d) ? q.get(d)[0] : 0, values ? rev.getOrDefault(d, BigDecimal.ZERO) : null));
        }
        List<TopItem> topItems = top.values().stream().sorted(Comparator.comparingInt(TopItem::quantity).reversed()).limit(6)
                .map(t -> values ? t : new TopItem(t.code(), t.name(), t.unit(), t.quantity(), null)).toList();
        List<ItemDto> lowItems = items.stream().filter(i -> "LOW".equals(i.stockStatus()))
                .sorted(Comparator.comparingInt(Item::getCurrentStock)).limit(8).map(i -> catalogService.toDto(i, values)).toList();
        return new DashboardDto(items.size(), low, out, over, values ? value : null, values ? debt : null, values ? overdue : null,
                openReceipts, pending, recount, openRecons, latest.orElse(null), upToDate, series, topItems, lowItems);
    }
}
