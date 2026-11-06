package vn.songdem.inventory.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message, List<String> details) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.value());
        m.put("code", code);
        m.put("message", message);
        m.put("details", details == null ? List.of() : details);
        return ResponseEntity.status(status).body(m);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return body(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage() == null ? f.getField() + " không hợp lệ" : f.getDefaultMessage())
                .distinct().toList();
        String msg = details.isEmpty() ? "Dữ liệu không hợp lệ" : details.get(0);
        return body(HttpStatus.BAD_REQUEST, "VALIDATION", msg, details);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleDenied(Exception ex) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleLock(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Dữ liệu vừa được người khác cập nhật. Vui lòng tải lại trang để xem trạng thái hiện tại.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleSize(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Tệp vượt quá dung lượng cho phép (tối đa 10MB)", null);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleBadInput(Exception ex) {
        log.debug("Bad input", ex);
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Thiếu thông tin hoặc dữ liệu gửi lên không hợp lệ", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("Lỗi không mong muốn", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau", null);
    }
}
