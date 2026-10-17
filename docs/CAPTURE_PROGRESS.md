# Theo dõi ảnh trong bảng nổi

Từ v0.11.0, bấm nút **Ảnh** có icon lịch sử ở đầu thanh nổi để mở/đóng bảng **Tiến trình ảnh** bên phải. Kéo tiêu đề để di chuyển, bấm × để đóng riêng bảng này. Bảng tự cập nhật khoảng mỗi 0,75 giây, giữ vị trí khi bạn cuộn xem ảnh cũ và tự theo ảnh mới khi đang ở cuối danh sách.

Hiển thị tối đa 30 ảnh gần nhất theo thứ tự cũ → mới: ảnh thu nhỏ, số thứ tự, thời gian, tên dễ đọc như **violet-1, violet-2**, nội dung ảnh, dung lượng và trạng thái. Không hiển thị URL hoặc đường dẫn lưu trữ. Tên được đánh số riêng theo tướng và lượt, lưu cố định trong nhật ký; xóa một ảnh không đổi số của ảnh sau. Đây là tên hiển thị trong bảng, giữ cấu trúc file gốc để tương thích thư viện và macro. Số thứ tự dùng chung cho ảnh chụp và ảnh gốc nhập từ Pictures, theo thứ tự bắt đầu ghi vào app. Ảnh Pictures có thể được nhập chậm hơn thao tác Sure; đây là thứ tự ghi thực tế, không phải ước tính tổng số quân.

- **Đang lưu**: đã bắt đầu ghi file, chưa xác nhận xong.
- **Đã lưu trong app**: file đã ghi và có dữ liệu; ảnh chụp được ghi qua file tạm rồi đổi tên.
- **Lưu thất bại / Lưu bị gián đoạn**: thao tác ghi lỗi hoặc dịch vụ trước bị ngắt.
- **File đã bị xóa hoặc di chuyển**: file từng lưu thành công không còn ở vị trí đã ghi.

Phía trên hiển thị bước macro hiện tại, ảnh gốc đã chép/đang chờ Pictures, tiến độ ghi ZIP và trạng thái Drive/ChatGPT của lượt. Ghi ZIP và đọc lại thành công vẫn là **chờ Drive xác nhận**, chưa phải đã tải lên cloud.

Nhật ký lưu bền 120 mục gần nhất trong bộ nhớ riêng của app, bắt đầu từ phiên bản này; không quét và suy đoán thứ tự của các bộ ảnh cũ. Nhật ký không chứa tài khoản hoặc câu lệnh ChatGPT, không đưa vào ZIP xuất dữ liệu. Đọc nhật ký và tạo ảnh thu nhỏ chạy nền, giới hạn kích thước ảnh thu nhỏ. Cả hai bảng tự ẩn và không nhận chạm khi macro chạm game hoặc chụp màn hình. Về app/Đóng bảng điều khiển cũng đóng bảng tiến trình. Xoay màn hình đưa bảng tiến trình về mép phải để không nằm ngoài màn hình.

Kiểm thử Android gồm ghi ảnh thật bằng worker, kiểm tra file và dung lượng, thứ tự nhật ký, trạng thái lỗi/gián đoạn/file bị xóa, mở/đóng bảng, cập nhật trực tiếp, xoay màn hình và ẩn/hiện trong thao tác chạm game. Các tích hợp Drive/ChatGPT vẫn cần kiểm tra trên thiết bị thật như mô tả trong AFTER_RUN.md.

Từ v0.12.0, bấm ảnh thu nhỏ hoặc dòng ảnh đã lưu để mở khung xem lớn ngay trên game. Khung nạp bản lớn từ file gốc ở luồng nền, không phóng lớn bản thumbnail. Dùng **+ / −**, chụm hai ngón hoặc chạm đúp để phóng to (tối đa 6×), kéo để xem phần khác; **Đóng** quay lại bảng lịch sử. File không còn đọc được có thông báo riêng. Khung xem cũng tự ẩn khi macro chụp/chạm game; đóng cùng bảng hoặc khi xoay màn hình. Giới hạn giải mã ảnh 4096 px mỗi cạnh và khoảng 6 triệu pixel để tránh giữ ảnh quá lớn trong RAM.

Ước lượng thời gian dùng lại RunTimes: chỉ học các lượt hoàn tất trọn vẹn, không bị dừng/tạm dừng/lỗi, theo đúng cấu hình macro (tướng/mục chọn/tốc độ/tải ảnh/mở game). Lưu tối đa 8 thời lượng gần đây cho mỗi cấu hình và trộn dần theo các lượt mới. Thời gian chạy đã có trong run.json; ước lượng lưu trong preferences để dùng lại sau khi mở app hoặc tạo lượt mới. Trước khi chạy hiển thị tổng dự kiến, khi chạy hiển thị thời gian còn lại; vượt mốc thì báo lâu hơn các lượt trước. Cấu hình chưa có dữ liệu ghi rõ cần một lượt hoàn tất, không đoán tổng quân Cờ hay hiển thị phần trăm. Ước lượng này dành cho thao tác thu thập, không bao gồm chờ Pictures/Drive/ChatGPT sau lượt.

Kiểm thử bổ sung: tên violet-1/2/3 và số không đổi sau xóa ảnh, mở ảnh lớn và phóng to/thu nhỏ, đóng về bảng, ẩn khung xem trong screenshot, không lộ đường dẫn trong dòng lịch sử, lưu/đọc lại thời gian cho đúng cấu hình tác vụ và hiển thị ước lượng đã học.
