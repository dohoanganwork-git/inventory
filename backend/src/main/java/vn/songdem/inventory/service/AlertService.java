package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.songdem.inventory.domain.Item;
import vn.songdem.inventory.domain.SalesExportLine;
import vn.songdem.inventory.dto.CatalogDtos.ItemDto;
import vn.songdem.inventory.dto.CommonDtos.RefDto;
import vn.songdem.inventory.repository.ItemRepository;
import vn.songdem.inventory.repository.SalesExportLineRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** UC_25 – Xem cảnh báo tồn thấp, tồn vượt mức và bán vượt tồn. */
@Service
@RequiredArgsConstructor
public class AlertService {
    private final ItemRepository itemRepository;
    private final SalesExportLineRepository salesLineRepository;
    private final CatalogService catalogService;

    public record OverSoldDto(String salesId, String salesCode, LocalDate salesDate, RefDto item, String unit,
                              int quantity, int stockBefore, int overQuantity) {}

    public record AlertsDto(List<ItemDto> lowStock, List<ItemDto> overStock, List<OverSoldDto> overSold,
                            int outOfStock) {}

    @Transactional(readOnly = true)
    public AlertsDto alerts(boolean withValues) {
        List<Item> all = itemRepository.findAllWithRefs().stream().filter(Item::isActive).toList();
        List<ItemDto> low = all.stream().filter(i -> "LOW".equals(i.stockStatus()))
                .sorted(Comparator.comparingDouble(i -> i.getMinStock() == 0 ? i.getCurrentStock() : (double) i.getCurrentStock() / i.getMinStock()))
                .map(i -> catalogService.toDto(i, withValues)).toList();
        List<ItemDto> over = all.stream().filter(i -> "OVER".equals(i.stockStatus()))
                .map(i -> catalogService.toDto(i, withValues)).toList();
        List<SalesExportLine> lines = salesLineRepository.findOverSoldSince(LocalDate.now().minusDays(30));
        List<OverSoldDto> overSold = lines.stream().map(l -> new OverSoldDto(l.getSalesExport().getId(), l.getSalesExport().getCode(),
                l.getSalesExport().getSalesDate(), RefDto.of(l.getItem()), l.getItem().getUnit(), l.getQuantity(), l.getStockBefore(),
                l.getOverQuantity())).toList();
        int outOfStock = (int) all.stream().filter(i -> i.getCurrentStock() == 0).count();
        return new AlertsDto(low, over, overSold, outOfStock);
    }
}
