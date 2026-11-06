package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.domain.StoredFile;
import vn.songdem.inventory.domain.StoredFileContent;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.repository.StoredFileContentRepository;
import vn.songdem.inventory.repository.StoredFileRepository;
import vn.songdem.inventory.web.ApiException;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Lưu tệp tải lên vào CSDL (phù hợp triển khai đám mây không có ổ đĩa cố định). */
@Service
@RequiredArgsConstructor
public class FileStorageService {
    public static final long MAX_SIZE = 10L * 1024 * 1024;
    public static final Set<String> DATA_EXT = Set.of("xlsx", "xls", "csv");
    public static final Set<String> EVIDENCE_EXT = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    public static final Set<String> DOCUMENT_EXT = Set.of("pdf", "jpg", "jpeg", "png", "webp", "doc", "docx");

    private final StoredFileRepository fileRepository;
    private final StoredFileContentRepository contentRepository;

    public record Loaded(StoredFile file, byte[] data) {}

    public static String extension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được tệp tải lên");
        }
    }

    public void validate(MultipartFile file, Set<String> allowed, String label) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Vui lòng chọn " + label);
        if (file.getSize() > MAX_SIZE) throw ApiException.badRequest(label + " vượt quá dung lượng cho phép (tối đa 10MB)");
        String ext = extension(file.getOriginalFilename());
        if (!allowed.contains(ext)) {
            throw ApiException.badRequest(label + " sai định dạng. Chấp nhận: " + String.join(", ", allowed.stream().sorted().toList()));
        }
    }

    @Transactional
    public StoredFile store(MultipartFile file, Set<String> allowed, String label, String category, User user) {
        validate(file, allowed, label);
        return storeBytes(file.getOriginalFilename(), file.getContentType(), bytes(file), category, user);
    }

    @Transactional
    public StoredFile storeBytes(String name, String contentType, byte[] data, String category, User user) {
        StoredFile f = new StoredFile();
        f.setOriginalName(name == null ? "tep-tin" : name);
        f.setContentType(contentType == null ? "application/octet-stream" : contentType);
        f.setSize(data.length);
        f.setCategory(category);
        f.setUploadedBy(user);
        fileRepository.save(f);
        StoredFileContent c = new StoredFileContent();
        c.setFileId(f.getId());
        c.setData(data);
        contentRepository.save(c);
        return f;
    }

    @Transactional(readOnly = true)
    public Loaded load(String id) {
        StoredFile f = fileRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tệp"));
        StoredFileContent c = contentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nội dung tệp"));
        return new Loaded(f, c.getData());
    }
}
