# Chiến khu – Thu thập đủ danh sách tướng

**Trạng thái:** thay đổi đang thử nghiệm trên nhánh `codex/fix-warzone-picker-full-roster`. Bản 0.13.26 (versionCode 57) kế thừa mã nhập mã sự kiện 0.13.25, không thay đổi chữ ký APK hoặc launcher icon.

## Luồng thao tác

1. Từ sảnh, vào **Đấu hạng → Chiến khu**. Nếu game yêu cầu hồi ức mùa giải hoặc khu vực, vẫn dùng luồng hiện có.
2. Mở **Chọn tướng** từ nút đổi tướng ở trang Chiến khu. **Mỗi lần mở**, xác minh tab **Tất cả** và kiểm tra **Đã nhận** đã bỏ tích. Nếu không nhận diện được, dừng và ghi ảnh lỗi, không lấy thiếu tướng.
3. Xác minh đầu danh sách bằng ảnh chân dung tướng (không dùng vị trí các viền hàng giống nhau). Khi mở lại popup, chỉ giữ vị trí cuộn đã đo được nếu ảnh chân dung khớp; ngược lại trở về đầu có kiểm chứng.
4. Chọn từng tướng theo **8 cột trái qua phải, hết hàng mới xuống hàng**. Đọc ảnh thật và đo khoảng cuộn thực tế sau mỗi vuốt; ảnh không đủ thông tin sẽ báo lỗi thay vì tự giả định đã đến cuối.
5. Thực hiện các tab/bảng Chiến khu được cấu hình, lưu ảnh và đánh dấu tướng đã chụp. Lặp đến hết danh sách hoặc đủ số tướng do người dùng chọn.
6. **Chỉ báo hoàn thành** khi nhìn thấy hàng cuối đầy đủ, khoảng trống sau hàng cuối, không có mẩu hàng tướng mới bị cắt ở đáy popup, và **hai lần vuốt xuống không di chuyển ảnh**. Hàng cuối 1–7 ô được nhận dạng đúng theo cột đầu tiên.

## Lỗi cũ và cách xử lý

| Lỗi | Thay đổi |
| --- | --- |
| Hàng cuối 1–4 tướng không được nhận diện vì không chiếm quá nửa 8 cột | Nhận dạng dải hàng từ cột tướng đầu tiên, xác minh các ô còn lại riêng |
| Vuốt ngắn hơn dự tính gần cuối bị đọc thành “không chuyển động” | Đo dịch chuyển RGB của chân dung tướng bằng ảnh trước/sau, không chỉ so vị trí hàng |
| Viền hàng cách đều có thể bị nhầm vị trí dẫn đến chọn sai hàng/trùng tướng | Dùng đăng ký ảnh chân dung có kết quả tin cậy; nếu không đủ bằng chứng thì dừng |
| Filter **Đã nhận** bị bật lại, hoặc game ghi nhớ một tab khác | Xác minh **Tất cả** và **Đã nhận** trước mỗi lần chọn |
| Vị trí cuộn thay đổi khi filter làm danh sách ngắn/dài | Chấp nhận dịch chuyển đảo chiều đã đo trong bước kiểm tra, sau đó tự về đầu |
| Một thao tác vuốt không di chuyển dẫn đến kết thúc quá sớm | Kiểm tra mép cuối, mẩu hàng bị cắt và hai lần vuốt liên tiếp có chứng cứ |

## Kiểm thử

- Sử dụng ảnh đã kiểm duyệt từ game trong `app/src/test/resources/screens/board-picker*.jpg`.
- Kiểm thử hàng cuối **1, 2, 3, 4** tướng; danh sách **129 tướng** (hàng cuối 1), tiếp tục từ tướng **126/137**, lựa chọn **Tất cả** lại sau mỗi lần popup được mở, và filter **Đã nhận**.
- Các kiểm thử cũ về bảng quốc gia, tỉnh, miền và top máy chủ vẫn bắt buộc.

**Giới hạn:** Ảnh UI mẫu có tỷ lệ ngang Liên Quân VN. Nếu giao diện game cập nhật, danh sách bị che bởi popup hoặc hiệu ứng hoạt ảnh làm nhận dạng chân dung không tin cậy, chương trình dừng và lưu ảnh chẩn đoán. Kết quả CI không thay thế kiểm tra từng thao tác trên điện thoại thật.

## Hướng dẫn xác minh thủ công

Chạy thử khoảng **10 tướng** trước, tiếp tục lên **tất cả tướng** khi ổn định. Để kiểm tra hàng cuối, so ảnh chụp và thứ tự trong lịch sử ứng dụng. Nếu bị dừng, giữ nguyên dữ liệu và gửi **ảnh chẩn đoán `board-picker-*` cùng thông báo lỗi**, không nên xóa ứng dụng hay xoá ảnh đã có.
