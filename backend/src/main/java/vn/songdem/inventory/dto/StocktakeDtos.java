package vn.songdem.inventory.dto;

import vn.songdem.inventory.dto.CommonDtos.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StocktakeDtos {
    private StocktakeDtos() {}

    public record StocktakeLineDto(String id, RefDto item, String unit, int bookQty, int damagedQty, String damageReason,
                                   int expectedQty, int countedQty, int varianceQty, int initialVarianceQty, int recountTimes,
                                   String decision, Integer adjustedQty, boolean needsDecision) {}

    public record SubmissionDto(String id, String kind, FileDto countFile, FileDto damageFile, UserRef submittedBy,
                                int varianceLines, LocalDateTime submittedAt) {}

    public record StocktakeDto(String id, String code, LocalDate stocktakeDate, String status, int round,
                               int totalLines, int matchedLines, int varianceLines, int pendingLines, int recountLines,
                               double accuracy, int damagedQty, String decisionReason, String recountReason,
                               UserRef createdBy, UserRef reviewedBy, LocalDateTime createdAt, LocalDateTime decidedAt,
                               FileDto countFile, FileDto damageFile,
                               List<StocktakeLineDto> lines, List<SubmissionDto> submissions, List<AuditDto> history) {}

    public record DecisionRequest(List<String> approveLineIds, List<String> recountLineIds, String reason) {}
}
