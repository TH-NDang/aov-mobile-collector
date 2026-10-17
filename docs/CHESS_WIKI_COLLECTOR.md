# Cờ Liên Quân — kế hoạch thu thập ảnh Bách khoa

Nhánh gốc: `claude/dark-theme`. Tài liệu này xác định cấu trúc đầu ra và các trạng thái phải xác minh trước khi tự động chạm màn hình; chưa phải một macro chạy được.

## Điều hướng
1. Dùng bước `enter(to=lobby)` hiện có để vào sảnh, xử lý đăng nhập và popup.
2. Từ sảnh chọn **Chọn chế độ** (không chọn N.vụ), rồi **Chế độ giải trí** → **Cờ Liên Quân** → **Bắt đầu**.
3. Tại sảnh Cờ Liên Quân, chọn **Trang chủ** ở thanh trái để mở **Bách khoa Cờ Liên Quân**.
4. Xác minh tiêu đề Bách khoa trước khi thu thập. Mọi màn hình không khớp phải tạm dừng và giữ ảnh kiểm tra, không chạm mò.

## Danh mục và cấu trúc ảnh
```text
captures/<run>/co-lien-quan/
  01-thong-tin-co-ban/<tab>/page-001.png ...
  02-quan-co/
    tat-ca-quan-co/page-001.png ...
    tat-ca-quan-co/<ten-tuong>/detail/page-001.png ...
    phe-va-nghe/page-001.png ...
  03-tuong-tac/
    phe/<ten-phe>/page-001.png ...
    nghe/<ten-nghe>/page-001.png ...
  04-trang-bi/
    danh-sach/<nhom>/page-001.png ...
    chi-tiet/<ten-trang-bi>/page-001.png ...
  05-nhiem-vu/page-001.png ...
  06-chuc-phuc/
    thuong/page-001.png ...
    huyen-thoai-su-thi/page-001.png ...
    truyen-thuyet/page-001.png ...
  manifest.json
```

- Ảnh gốc giữ nguyên; không OCR hay ghép ảnh làm mất bằng chứng.
- Lưu thứ tự tab, tọa độ thao tác, timestamp, kích thước màn hình, số trang và trạng thái hoàn thành trong manifest.
- Tab ngang phải được cuộn đến hết; danh sách dọc được chụp theo từng viewport với phần chồng lấn.
- Chỉ cuộn trong vùng nội dung, tránh thanh menu cố định.
- Phát hiện cuối danh sách bằng so sánh nội dung vùng cuộn giữa nhiều lần chụp, không chỉ đếm số lần vuốt.
- Mỗi ô quân cờ và biểu tượng phe/nghề, trang bị có thể mở panel; chụp panel rồi đóng bằng X, quay lại đúng vị trí cuộn.
- Khi panel chi tiết có cuộn riêng, thu thập toàn bộ trước khi đóng.
- Tạm dừng, tiếp tục, dừng và xuất ZIP dùng cơ chế hiện có; chạy lại phải bỏ qua phần hoàn thành và không ghi đè ảnh.
- Khi OCR tên không chắc chắn, dùng ID vị trí ổn định và ghi tên suy đoán trong metadata; không coi OCR là dữ liệu chắc chắn.
- Không tự ý vào trận đấu, mua vật phẩm hoặc đổi tài khoản.

## Điều kiện hoàn tất
Có bài test với ảnh chụp thật cho nhận diện từng màn hình, điểm chạm, thay tab, cuộn, popup và kết thúc trang; có kiểm thử trên thiết bị Android với AccessibilityService; build APK và lưu artifact bằng GitHub Actions.
