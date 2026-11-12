package vn.songdem.inventory.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Xử lý bảng mã file CSV để làm việc tốt với Excel trên Windows.
 * <ul>
 *   <li>Excel chỉ nhận đúng UTF-8 khi đầu file có BOM (EF BB BF), nếu không tiếng Việt bị lỗi font.</li>
 *   <li>Lưu "CSV (Comma delimited)" trong Excel tiếng Việt cho ra bảng mã Windows-1258 thay vì UTF-8.</li>
 * </ul>
 */
public final class CsvEncoding {
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    /** Bảng mã mặc định của Excel/Windows tiếng Việt. */
    private static final Charset WINDOWS_VN = Charset.forName("windows-1258");

    private CsvEncoding() {}

    private static boolean hasBom(byte[] data) {
        return data.length >= 3 && data[0] == BOM[0] && data[1] == BOM[1] && data[2] == BOM[2];
    }

    /** Đọc nội dung CSV: ưu tiên UTF-8 (có/không BOM), nếu không hợp lệ thì đọc theo Windows-1258. */
    public static String decode(byte[] data) {
        if (hasBom(data)) return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            // Windows-1258 lưu dấu thanh tách rời (vd: "o" + dấu hỏi) – ghép lại thành chữ dựng sẵn
            return Normalizer.normalize(new String(data, WINDOWS_VN), Normalizer.Form.NFC);
        }
    }

    /** Chuyển CSV sang UTF-8 có BOM để mở bằng Excel không bị lỗi font. */
    public static byte[] forExcel(byte[] data) {
        if (hasBom(data)) return data;
        byte[] utf8 = decode(data).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[utf8.length + 3];
        System.arraycopy(BOM, 0, out, 0, 3);
        System.arraycopy(utf8, 0, out, 3, utf8.length);
        return out;
    }
}
