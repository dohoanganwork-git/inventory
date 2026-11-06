package vn.songdem.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        // Toàn bộ nghiệp vụ tính theo giờ Việt Nam (đặt trước khi khởi tạo kết nối CSDL)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(InventoryApplication.class, args);
    }
}
