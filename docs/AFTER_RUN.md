# Sau khi hoàn tất: Drive và dot của bạn

Trên màn hình chính, bấm **Sau khi hoàn tất · Drive → dot của bạn**.

1. Bấm **Chọn ZIP đích trên Google Drive**. Trong bộ chọn Android, mở Google Drive, chọn tài khoản và thư mục muốn lưu, rồi tạo `aov-collector-latest.zip`. Mỗi lượt hoàn tất sẽ cập nhật **chỉ file ZIP đích này**, thay kết quả của lượt trước. Tùy chọn này dùng thao tác tạo file, không phụ thuộc Drive có hiện trong bộ chọn thư mục hay không. Nếu muốn giữ ZIP riêng từng lượt và Drive có hỗ trợ bộ chọn thư mục, dùng **Chọn thư mục Drive (nếu có)**.
2. Bật **Tự lưu kết quả lên Drive sau khi hoàn tất lượt**. Kết quả được đóng ZIP và ghi tại vị trí đã chọn. ZIP chứa thư mục của lượt và ảnh gốc Cờ Liên Quân đã nhập của cùng lượt, giữ cấu trúc thư mục. Tài khoản Garena và cấu hình câu lệnh nằm ngoài dữ liệu xuất.
3. Nếu muốn gửi tiếp, bật **Drive tải xong → mở ChatGPT → dot của bạn → gửi câu lệnh**, nhập câu lệnh rồi bấm **Lưu**. App giữ nguyên câu lệnh; không tự thêm đường dẫn hay đổi nội dung.
4. Bật thông báo của Google Drive, giữ Trợ năng của AOV Collector hoạt động và đăng nhập sẵn Drive/ChatGPT. Tính năng chạy khi thu thập hoàn tất, không chạy lúc tạm dừng hoặc bấm Dừng lượt giữa chừng.

App chờ nhập xong ảnh gốc của lượt, ghi ZIP vào thư mục Drive, rồi đọc lại và so sánh SHA-256 với dữ liệu đã ghi. **Ghi xong và đọc lại đúng chưa đồng nghĩa đã tải xong lên máy chủ.** App chỉ chuyển sang ChatGPT sau thông báo tải thành công của Google Drive có đúng tên ZIP đó và không mang trạng thái tải dở/lỗi. Nếu không có xác nhận phù hợp, app giữ trạng thái chờ trong mục Sau khi hoàn tất, không tự gửi câu lệnh. Có nút mở Drive kiểm tra và dừng thao tác sau lượt.

Trong ChatGPT, app mở menu bên trái, chọn đúng **dot của bạn** (hoặc Your dot), chờ tiêu đề đích hiện ra và menu đóng, kiểm tra ô nhập rồi mới điền lệnh và bấm Gửi. Nếu có bản nháp khác hoặc giao diện không nhận diện được, tác vụ dừng với trạng thái để kiểm tra. App ghi mốc trước khi bấm Gửi; sau khi bị gián đoạn ở bước này không tự gửi lần nữa. Cần tự xem ChatGPT khi trạng thái báo chưa xác nhận lần gửi.

Các lượt xếp hàng và xử lý lần lượt: không ghi đè ZIP đích cho lượt tiếp theo khi lượt trước còn đang chờ Drive hoặc chưa xử lý xong lệnh ChatGPT. Màn hình Sau khi hoàn tất hiển thị tác vụ đầu hàng để thử lại, dừng hoặc bỏ qua lần gửi chưa xác nhận.

Tiến độ được lưu riêng theo lượt để cập nhật app hoặc khởi động lại không gây gửi trùng. Cấu hình thay đổi áp dụng cho lượt hoàn tất tiếp theo. Lượt lưu Drive bị lỗi có thể thử lại bằng nút tương ứng. Chế độ ZIP đích ghi lại vào đúng file đã tạo; chế độ thư mục có thể để lại ZIP dở và Drive có thể tạo tên có số khi thử lại. Thông báo tải xong có thời gian trước lần ghi hiện tại không được dùng để xác nhận một lần cập nhật ZIP mới.

Kiểm thử tự động dùng DocumentsProvider thật trong APK kiểm thử để ghi/đọc ZIP và một app giao diện mô phỏng riêng để kiểm tra điều hướng dot và gửi lệnh. Module `chat-fixture` chỉ được cài trong máy ảo CI, không đóng gói vào APK AOV Collector. Không coi kiểm thử mô phỏng là xác nhận tương thích với mọi phiên bản Drive/ChatGPT trên điện thoại.
