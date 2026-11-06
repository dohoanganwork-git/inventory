# Ứng dụng web quản lý tồn kho và nhà cung cấp – Công ty TNHH Sông Đêm

Sản phẩm của khóa luận tốt nghiệp **“Xây dựng ứng dụng web quản lý tồn kho và nhà cung cấp cho Công ty TNHH Sông Đêm”**
(Đỗ Hoàng An – 65A.HTQL, Trường Công nghệ – ĐH Kinh tế Quốc dân). Hệ thống phục vụ cơ sở **LongWaits Jazz Club**
(số 5 Nguyễn Quang Bích, Hoàn Kiếm, Hà Nội) – nơi nhập đồ uống đóng lon/chai, rượu, bia, merchandise về để bán lại.

## Công nghệ (theo Chương 2)

| Thành phần | Công nghệ |
|---|---|
| Giao diện (frontend) | ReactJS 18.3.1 + TypeScript, Vite, Ant Design 5 (giao diện tiếng Việt), Recharts |
| Máy chủ (backend) | Java 21, Spring Boot 3 (Spring MVC, Spring Data JPA, Spring Security + JWT), Gradle |
| Thư viện | Lombok, ModelMapper, iText PDF (xuất PDF), Apache POI (đọc/xuất Excel), Commons CSV |
| Cơ sở dữ liệu | MySQL 8 (bảng đặt tên theo sơ đồ lớp – Hình 3.17) |
| Triển khai | Frontend: Vercel · Backend: Render (Docker) · MySQL: Aiven for MySQL / TiDB Cloud · Chạy local: Docker Compose |
| Quản lý mã nguồn | Git & GitHub |

Kiến trúc 3 tầng: trình duyệt (React) → RESTful API (Spring Boot: Controller → Service → Repository) → MySQL.

## Chức năng – đối chiếu với các use case

Phân quyền theo 3 vai trò: **Quản lý**, **Nhân viên**, **Kế toán**. Nhân viên chỉ xem số lượng; số liệu giá trị và công nợ chỉ hiển thị với Quản lý và Kế toán.

| Nhóm | Use case | Màn hình |
|---|---|---|
| Quản lý tài khoản | UC_01 Đăng nhập · UC_02 Đăng xuất · UC_03 Đổi mật khẩu (≥ 8 ký tự, khác mật khẩu cũ) · UC_04 Cập nhật thông tin cá nhân | Đăng nhập, menu người dùng, *Tài khoản của tôi* |
| | UC_05 Tạo tài khoản · UC_06 Sửa / vô hiệu hóa / kích hoạt lại (không xóa; chặn tự vô hiệu hóa và khóa Quản lý cuối cùng) | *Tài khoản người dùng* (Quản lý) |
| Quản lý danh mục | UC_07 Thêm · UC_08 Sửa / ngừng sử dụng (cảnh báo khi còn tồn) · UC_09 Xem, tìm kiếm theo bộ lọc | *Loại hàng*, *Mặt hàng* (mã, mã POS, ĐVT, mốc cảnh báo), *Nhà cung cấp & hợp đồng* (liên hệ, hợp đồng, tệp hợp đồng) |
| Nhập / xuất | UC_10 Tạo phiếu nhập (tăng tồn, tự tạo công nợ, cảnh báo trùng số hóa đơn) | *Phiếu nhập hàng* → *Tạo phiếu nhập* |
| | UC_11 Nhập file doanh số POS (xem trước, chặn nhập trùng ngày, báo mã lạ, trừ tồn tối thiểu bằng 0, cảnh báo bán vượt tồn) | *Doanh số POS* → *Nhập doanh số POS* |
| | UC_12 Sửa / hủy phiếu nhập, phiếu xuất (chỉ Quản lý, bắt buộc lý do, lưu lịch sử; chặn khi phiếu đã có thanh toán hoặc làm tồn âm) · UC_13 Lịch sử phiếu | Chi tiết phiếu nhập / phiếu xuất |
| Công nợ | UC_14 Xem đơn nhập & công nợ theo NCC · UC_15 Thanh toán (một hoặc nhiều phiếu cùng NCC, kèm minh chứng) · UC_16 Sửa / hủy thanh toán (kèm lý do) | *Công nợ nhà cung cấp*, chi tiết phiếu nhập |
| | UC_17 Đối soát công nợ theo kỳ bằng file hóa đơn NCC: đánh dấu khớp / lệch tiền / lệch số đã trả / hóa đơn chưa có phiếu / phiếu không có trong file; ghi chú, tải lại, hoàn tất & khóa kỳ, mở lại kỳ | *Đối soát công nợ* |
| Tồn kho | UC_18 Xem tồn kho theo thời gian thực (lọc theo loại, NCC, trạng thái tồn; thẻ kho từng mặt hàng) | *Tồn kho*, *Mặt hàng* |
| Kiểm kê & đối soát kho | UC_19 Tải file tồn thực tế + file hỏng/hủy · UC_20 Thay file / gửi file kiểm kê lại · UC_21 Xem kết quả đối soát · UC_22 Xác nhận điều chỉnh (toàn bộ hoặc một phần) · UC_23 Yêu cầu kiểm kê lại | *Kiểm kê & đối soát kho* |
| Cảnh báo | UC_24 Thiết lập mốc cảnh báo (và mức tồn tối đa) · UC_25 Xem cảnh báo tồn thấp, hết hàng, tồn vượt mức, bán vượt tồn + thông báo (chuông) | *Cảnh báo tồn kho*, chuông thông báo |
| Báo cáo | UC_26: Giá trị tồn kho · Nhập – xuất – tồn · Tiêu thụ (kèm hàng không bán được) · Công nợ NCC · Kiểm kê & tỉ lệ chính xác kho. Xuất **Excel** và **PDF** | *Báo cáo thống kê* |

Nguyên tắc nghiệp vụ quan trọng:
- **Tồn kho chỉ thay đổi khi Quản lý xác nhận** kết quả kiểm kê; file kiểm kê của Nhân viên chỉ dùng để đối chiếu.
  Chênh lệch = Thực tế − (Sổ sách − Hỏng/hủy). Mỗi thời điểm chỉ có một đợt kiểm kê đang mở.
- Phải nhập doanh số POS đến ngày trước ngày kiểm kê. Không nhập được doanh số của ngày đã kiểm kê hoàn tất.
- Mọi biến động tồn được ghi vào **thẻ kho**: Tồn cuối kỳ = Tồn đầu kỳ + Nhập − Xuất + Điều chỉnh.
- Khoản thanh toán thuộc kỳ đối soát đã hoàn tất sẽ bị khóa (phải mở lại kỳ mới sửa được).
- Tệp tải lên (hợp đồng, minh chứng, file POS, file kiểm kê) lưu trong MySQL để không mất khi máy chủ khởi động lại.

## Tài khoản dùng thử

Khi CSDL trống, hệ thống tự tạo dữ liệu mẫu cho 35 ngày gần nhất: 19 mặt hàng (bia Tê Tê, Thơm Brewing, Hanoi Cider, vang Bewine,
Riesling, Vistana, Junbuncha, Bundaberg, CD, USB Nàng Thơ, túi tote…), 8 nhà cung cấp, phiếu nhập, doanh số POS hằng ngày, thanh toán,
3 đợt kiểm kê (1 đợt đang chờ Quản lý xem xét) và 2 kỳ đối soát công nợ. Thông tin liên hệ và tên người dùng là dữ liệu giả lập.

| Vai trò | Tên đăng nhập | Mật khẩu |
|---|---|---|
| Quản lý | `quanly` | `songdem123` |
| Nhân viên | `nhanvien` | `songdem123` |
| Kế toán | `ketoan` | `songdem123` |

Tắt dữ liệu mẫu bằng biến môi trường `SEED_DATA=false` (khi đó cần tạo tài khoản Quản lý đầu tiên trực tiếp trong CSDL hoặc bật seed một lần rồi đổi mật khẩu).

## Chạy trên máy

### Cách 1 – Docker Compose (khuyên dùng)

```bash
docker compose up -d --build
```

Mở http://localhost:3000. Lần đầu khởi động mất khoảng 1–2 phút (tải thư viện, tạo dữ liệu mẫu).

### Cách 2 – Chạy từng phần

Yêu cầu: JDK 21, Node.js 20+, MySQL 8.

```bash
mysql -uroot -p -e "CREATE DATABASE kho_songdem CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER 'kho'@'%' IDENTIFIED BY 'kho123'; GRANT ALL ON kho_songdem.* TO 'kho'@'%';"
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

Mở http://localhost:5173 (Vite chuyển tiếp `/api` sang `http://localhost:8080`).

Chạy kiểm thử backend (H2 ở chế độ MySQL): `cd backend && ./gradlew test`.

## Triển khai online

Vercel chỉ chạy được frontend; backend Spring Boot chạy trên Render, MySQL đặt ở dịch vụ đám mây.

1. **MySQL**: tạo CSDL miễn phí trên Aiven for MySQL hoặc TiDB Cloud (tương thích MySQL). Lấy chuỗi kết nối, ví dụ
   `jdbc:mysql://<host>:<port>/kho_songdem?sslMode=REQUIRED&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=utf8`.
2. **Backend (Render)**: New → Blueprint → chọn repo (file `render.yaml`), hoặc New → Web Service → Docker, *Root Directory* `backend`.
   Đặt biến môi trường:

   | Biến | Ý nghĩa |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối MySQL |
   | `JWT_SECRET` | Chuỗi bí mật ký token (≥ 32 ký tự) |
   | `CORS_ALLOWED_ORIGINS` | Tên miền frontend, ví dụ `https://kho-songdem.vercel.app` (hỗ trợ mẫu `https://*.vercel.app`) |
   | `SEED_DATA` | `true` để tạo dữ liệu mẫu khi CSDL trống |

   Gói miễn phí của Render cho máy chủ “ngủ” khi không dùng – lần truy cập đầu tiên có thể mất 30–60 giây.
3. **Frontend (Vercel)**: Import repo, *Root Directory* `frontend`, Framework *Vite*, biến môi trường
   `VITE_API_URL=https://<ten-dich-vu>.onrender.com`. File `vercel.json` đã cấu hình chuyển hướng cho ứng dụng một trang.

## Định dạng file tải lên

Mỗi màn hình có nút **Tải file mẫu** (Excel, kèm sheet hướng dẫn). Hỗ trợ `.xlsx`, `.xls`, `.csv` (UTF-8, dấu phẩy hoặc chấm phẩy), tối đa 10MB.
Tên cột không phân biệt hoa thường/dấu.

| File | Cột bắt buộc | Cột tùy chọn |
|---|---|---|
| Doanh số POS | Mã hàng POS, Số lượng | Tên hàng, Đơn giá / Thành tiền |
| Kiểm kê tồn thực tế | Mã hàng (hoặc mã POS), Số lượng thực tế | Tên hàng |
| Hỏng / hủy | Mã hàng, Số lượng hỏng/hủy, Lý do | |
| Hóa đơn NCC (đối soát) | Số hóa đơn, Số tiền | Ngày hóa đơn, Đã thanh toán |

## Cấu trúc mã nguồn

```
backend/                     Spring Boot (Java 21, Gradle)
  src/main/java/vn/songdem/inventory/
    domain/                  Thực thể JPA (nguoi_dung, mat_hang, nha_cung_cap, phieu_nhap_hang, phieu_xuat_ban,
                             thanh_toan_cong_no, kiem_ke_va_doi_soat, chi_tiet_kiem_ke, the_kho, ...)
    repository/              Spring Data JPA
    service/                 Nghiệp vụ (nhập hàng, POS, công nợ, đối soát, kiểm kê, cảnh báo...)
    report/                  Báo cáo + xuất Excel (Apache POI) / PDF (iText)
    web/                     REST controller, xử lý lỗi
    security/                JWT, phân quyền
    config/                  Cấu hình, dữ liệu mẫu
  src/test/                  Kiểm thử luồng nghiệp vụ
frontend/                    React 18.3.1 + TypeScript + Ant Design
  src/pages/                 Các màn hình
  src/components/            Thành phần dùng chung (layout, biểu đồ, thẻ trạng thái, tải tệp...)
docker-compose.yml           Chạy toàn bộ hệ thống trên máy
render.yaml                  Cấu hình triển khai backend trên Render
```
