package vn.songdem.inventory.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** Sinh mã chứng từ dạng PN260105-001. */
public final class CodeGenerator {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyMMdd");

    private CodeGenerator() {}

    public static String next(String prefix, LocalDate date, Function<String, Long> counter) {
        String base = prefix + date.format(F) + "-";
        long n = counter.apply(base) + 1;
        return base + String.format("%03d", n);
    }
}
