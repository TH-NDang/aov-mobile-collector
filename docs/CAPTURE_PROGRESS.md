# Theo dõi ảnh trong bảng nổi

Từ v0.11.0, bấm nút **Ảnh** có icon lịch sử ở đầu thanh nổi để mở/đóng bảng **Tiến trình ảnh** bên phải. Kéo tiêu đề để di chuyển, bấm × để đóng riêng bảng này. Bảng tự cập nhật khoảng mỗi 0,75 giây, giữ vị trí khi bạn cuộn xem ảnh cũ và tự theo ảnh mới khi đang ở cuối danh sách.

Hiển thị tối đa 30 ảnh gần nhất theo thứ tự cũ → mới: ảnh thu nhỏ, số thứ tự, thời gian, đường dẫn tương đối trong thư viện app, dung lượng và trạng thái. Số thứ tự dùng chung cho ảnh chụp và ảnh gốc nhập từ Pictures, theo thứ tự bắt đầu ghi vào app. Ảnh Pictures có thể được nhập chậm hơn thao tác Sure; đây là thứ tự ghi thực tế, không phải ước tính tổng số quân.

- **Đang lưu**: đã bắt đầu ghi file, chưa xác nhận xong.
- **Đã lưu trong app**: file đã ghi và có dữ liệu; ảnh chụp được ghi qua file tạm rồi đổi tên.
- **Lưu thất bại / Lưu bị gián đoạn**: thao tác ghi lỗi hoặc dịch vụ trước bị ngắt.
- **File đã bị xóa hoặc di chuyển**: file từng lưu thành công không còn ở vị trí đã ghi.

Phía trên hiển thị bước macro hiện tại, ảnh gốc đã chép/đang chờ Pictures, tiến độ ghi ZIP và trạng thái Drive/ChatGPT của lượt. Ghi ZIP và đọc lại thành công vẫn là **chờ Drive xác nhận**, chưa phải đã tải lên cloud.

Nhật ký lưu bền 120 mục gần nhất trong bộ nhớ riêng của app, bắt đầu từ phiên bản này; không quét và suy đoán thứ tự của các bộ ảnh cũ. Nhật ký không chứa tài khoản hoặc câu lệnh ChatGPT, không đưa vào ZIP xuất dữ liệu. Đọc nhật ký và tạo ảnh thu nhỏ chạy nền, giới hạn kích thước ảnh thu nhỏ. Cả hai bảng tự ẩn và không nhận chạm khi macro chạm game hoặc chụp màn hình. Về app/Đóng bảng điều khiển cũng đóng bảng tiến trình. Xoay màn hình đưa bảng tiến trình về mép phải để không nằm ngoài màn hình.

Kiểm thử Android gồm ghi ảnh thật bằng worker, kiểm tra file và dung lượng, thứ tự nhật ký, trạng thái lỗi/gián đoạn/file bị xóa, mở/đóng bảng, cập nhật trực tiếp, xoay màn hình và ẩn/hiện trong thao tác chạm game. Các tích hợp Drive/ChatGPT vẫn cần kiểm tra trên thiết bị thật như mô tả trong AFTER_RUN.md.
