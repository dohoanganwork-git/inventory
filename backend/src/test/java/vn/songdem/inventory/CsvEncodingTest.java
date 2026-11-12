package vn.songdem.inventory;

import org.junit.jupiter.api.Test;
import vn.songdem.inventory.util.CsvEncoding;
import vn.songdem.inventory.util.TabularFile;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvEncodingTest {
    private static final String CSV = "Mã hàng,Số lượng hỏng/hủy,Lý do\nHNC-TAO,2,\"Lon bị móp, rò rỉ\"\n";

    @Test
    void downloadAddsBomOnceAndKeepsText() {
        byte[] out = CsvEncoding.forExcel(CSV.getBytes(StandardCharsets.UTF_8));
        assertThat(out[0]).isEqualTo((byte) 0xEF);
        assertThat(out[1]).isEqualTo((byte) 0xBB);
        assertThat(out[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(out, 3, out.length - 3, StandardCharsets.UTF_8)).isEqualTo(CSV);
        assertThat(CsvEncoding.forExcel(out)).isEqualTo(out); // không thêm BOM lần hai
    }

    /** Mô phỏng cách Excel lưu "CSV (Comma delimited)" tiếng Việt: chữ gốc + dấu thanh rời, bảng mã Windows-1258. */
    private static byte[] excelWindows1258(String s) throws Exception {
        String tones = "\u0300\u0301\u0303\u0309\u0323";
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            StringBuilder base = new StringBuilder(), tone = new StringBuilder();
            for (char x : d.toCharArray()) (tones.indexOf(x) >= 0 ? tone : base).append(x);
            out.append(Normalizer.normalize(base, Normalizer.Form.NFC)).append(tone);
        }
        var enc = Charset.forName("windows-1258").newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPORT).onMalformedInput(CodingErrorAction.REPORT);
        var buf = enc.encode(CharBuffer.wrap(out));
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }

    @Test
    void readsUtf8WithBomAndWindows1258() throws Exception {
        byte[] withBom = CsvEncoding.forExcel(CSV.getBytes(StandardCharsets.UTF_8));
        byte[] ansi = excelWindows1258(CSV);
        for (byte[] data : new byte[][]{withBom, ansi}) {
            TabularFile tf = TabularFile.read("hong-huy.csv", data);
            String reasonCol = tf.findColumn("Lý do");
            assertThat(reasonCol).isNotNull();
            assertThat(tf.rows()).hasSize(1);
            assertThat(tf.rows().get(0).get(reasonCol)).isEqualTo("Lon bị móp, rò rỉ");
        }
    }
}
