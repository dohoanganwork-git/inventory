package vn.songdem.inventory.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** MultipartFile trong bộ nhớ – dùng khi tạo dữ liệu mẫu. */
public class BytesMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] data;

    public BytesMultipartFile(String name, String originalFilename, String contentType, byte[] data) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.data = data;
    }

    @Override public String getName() { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return data.length == 0; }
    @Override public long getSize() { return data.length; }
    @Override public byte[] getBytes() { return data; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
    @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), data); }
}
