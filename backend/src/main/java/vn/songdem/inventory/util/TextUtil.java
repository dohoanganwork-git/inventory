package vn.songdem.inventory.util;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

public final class TextUtil {
    private TextUtil() {}

    /** Chuẩn hóa chuỗi để so khớp tiêu đề cột: bỏ dấu, chữ thường, gộp khoảng trắng. */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = s.replace('đ', 'd').replace('Đ', 'D');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return t;
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Đọc số từ chuỗi theo cả định dạng Việt Nam (1.000.000,5) và quốc tế (1,000,000.5).
     * Trả về null nếu chuỗi rỗng; ném NumberFormatException nếu không phải số.
     */
    public static BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("(?i)(vnd|vnđ|đ|\\s)", "");
        if (s.isEmpty()) return null;
        if (!s.matches("-?[0-9.,]+")) throw new NumberFormatException(raw);
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            char dec = dot > comma ? '.' : ',';
            char thou = dec == '.' ? ',' : '.';
            s = s.replace(String.valueOf(thou), "").replace(dec, '.');
        } else if (dot >= 0 || comma >= 0) {
            char sep = dot >= 0 ? '.' : ',';
            long count = s.chars().filter(c -> c == sep).count();
            int digitsAfter = s.length() - s.lastIndexOf(sep) - 1;
            if (count > 1 || digitsAfter == 3) {
                s = s.replace(String.valueOf(sep), "");
            } else {
                s = s.replace(sep, '.');
            }
        }
        return new BigDecimal(s);
    }

    /** Đọc số nguyên; ném NumberFormatException nếu có phần thập phân. */
    public static Integer parseInt(String raw) {
        BigDecimal d = parseDecimal(raw);
        if (d == null) return null;
        try {
            return d.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            throw new NumberFormatException(raw);
        }
    }
}
