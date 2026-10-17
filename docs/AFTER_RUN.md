# Sau khi hoàn tất: Drive và dot của bạn

Trên màn hình chính, bấm **Sau khi hoàn tất · Drive → dot của bạn**.

1. Bấm **Chọn thư mục Google Drive**. Trong bộ chọn Android, mở Google Drive, chọn tài khoản Drive và thư mục đích, rồi cấp quyền sử dụng thư mục. Cần phiên bản Drive có hỗ trợ chọn thư mục qua bộ chọn Android; chọn thư mục bộ nhớ trong không được tính là chọn Drive.
2. Bật **Tự lưu kết quả lên Drive sau khi hoàn tất lượt**. Mỗi lượt được đóng thành một ZIP tại thư mục đã chọn. ZIP chứa thư mục của lượt và ảnh gốc Cờ Liên Quân đã nhập của cùng lượt, giữ cấu trúc thư mục. Tài khoản Garena và cấu hình câu lệnh nằm ngoài dữ liệu xuất.
3. Nếu muốn gửi tiếp, bật **Drive tải xong → mở ChatGPT → dot của bạn → gửi câu lệnh**, nhập câu lệnh rồi bấm **Lưu**. App giữ nguyên câu lệnh; không tự thêm đường dẫn hay đổi nội dung.
4. Bật thông báo của Google Drive, giữ Trợ năng của AOV Collector hoạt động và đăng nhập sẵn Drive/ChatGPT. Tính năng chạy khi thu thập hoàn tất, không chạy lúc tạm dừng hoặc bấm Dừng lượt giữa chừng.

App chờ nhập xong ảnh gốc của lượt, ghi ZIP vào thư mục Drive, rồi đọc lại và so sánh SHA-256 với dữ liệu đã ghi. **Ghi xong và đọc lại đúng chưa đồng nghĩa đã tải xong lên máy chủ.** App chỉ chuyển sang ChatGPT sau thông báo tải thành công của Google Drive có đúng tên ZIP đó và không mang trạng thái tải dở/lỗi. Nếu không có xác nhận phù hợp, app giữ trạng thái chờ trong mục Sau khi hoàn tất, không tự gửi câu lệnh. Có nút mở Drive kiểm tra và dừng thao tác sau lượt.

Trong ChatGPT, app mở menu bên trái, chọn đúng **dot của bạn** (hoặc Your dot), chờ tiêu đề đích hiện ra và menu đóng, kiểm tra ô nhập rồi mới điền lệnh và bấm Gửi. Nếu có bản nháp khác hoặc giao diện không nhận diện được, tác vụ dừng với trạng thái để kiểm tra. App ghi mốc trước khi bấm Gửi; sau khi bị gián đoạn ở bước này không tự gửi lần nữa. Cần tự xem ChatGPT khi trạng thái báo chưa xác nhận lần gửi.

Tiến độ được lưu riêng theo lượt để cập nhật app hoặc khởi động lại không gây gửi trùng. Cấu hình thay đổi áp dụng cho lượt hoàn tất tiếp theo. Lượt lưu Drive bị lỗi có thể thử lại bằng nút tương ứng; file ZIP dở trên Drive có thể vẫn còn và Drive có thể tạo tên có số khi thử lại.

Kiểm thử tự động dùng DocumentsProvider thật trong APK kiểm thử để ghi/đọc ZIP và một app giao diện mô phỏng riêng để kiểm tra điều hướng dot và gửi lệnh. Module `chat-fixture` chỉ được cài trong máy ảo CI, không đóng gói vào APK AOV Collector. Không coi kiểm thử mô phỏng là xác nhận tương thích với mọi phiên bản Drive/ChatGPT trên điện thoại.
