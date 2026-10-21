# Đề cử Pro · Combo tướng (v0.13.16)

## Cách chạy

1. Mở Liên Quân thủ công tới sảnh chính hoặc **Trang bị → Chuẩn bị**. Macro không đăng nhập, chọn tài khoản, mua/nhận tướng hay vào trận.
2. Trong bảng nổi chọn **Đề cử Pro · Combo tướng**, rồi **Chạy**. Có thể dùng **Chỉnh từng bước** trên đúng luồng thao tác; điểm và đường vuốt đã lưu được áp dụng trong giới hạn nhận diện/an toàn.
3. Macro mở đổi tướng bằng hai mũi tên phía dưới trái. Xác minh **Tất cả**, chỉ bỏ tick **Đã nhận** khi đọc rõ đang bật, sau đó đọc lại để xác minh tắt. Duyệt trái sang phải, trên xuống dưới; khoảng cuộn được đo trên ảnh, không suy ra từ độ dài vuốt.
4. Với mỗi tướng, mở **Phương án khác → Đề cử Pro** (bảng combo hoàn chỉnh). Ghi nhận thứ tự ban đầu, nhận diện lại đúng đề cử sau mỗi lần mở, không giả định top hiện tại giữ nguyên.
5. Bấm **Dùng combo** cho tối đa năm đề cử thực có. Chỉ chụp màn **Chuẩn bị** sau khi xác minh đúng tướng và các thành phần combo nhìn thấy khớp đề cử. Sau khi thu thập thành công, áp dụng lại đúng top 1 ban đầu, không chụp thêm và không khôi phục bộ cũ.

## Ảnh và tiến độ

- Mỗi tướng có thư mục theo tên, ảnh `<ten-tuong>-top-1.png` đến `top-5.png`. Không lặp ảnh để đủ năm.
- Danh sách không có đề cử chỉ được kết luận khi đọc được thông báo trống rõ ràng trong tab Pro. Lưu `<ten-tuong>-empty-pro-list.png` ngay tại danh sách, không chụp lại bộ cũ ở Chuẩn bị.
- `pro-progress.json` lưu tên tướng, thứ tự người đề cử, dấu hiệu hình ảnh, số ảnh, trạng thái trống/thu thập, thao tác đang chờ và nghĩa vụ trả lại top 1. File được đồng bộ rồi thay thế nguyên tử; file hỏng không bị xem là lượt mới.
- Tạm dừng/Dừng ngăn các thao tác tiếp theo. Một thao tác Android đã gửi có thể hoàn tất. Khi tiếp tục, thao tác áp dụng chưa rõ kết quả được đối chiếu với màn hình trước, không tự bấm lại. Ảnh đang lưu được kiểm tra/reconcile để tránh chụp trùng; nếu chưa lưu, phải xác minh lại đúng màn hình trước khi chụp.
- Nếu bị ngắt trước khi trả lại top 1, tiến độ giữ trạng thái cần khôi phục. Không báo hoàn tất cho tướng đó trước khi xác minh top 1.

## Giới hạn và xử lý lỗi

- Đã thêm kiểm thử máy trạng thái và lớp ghi tiến độ; **chưa kiểm chứng luồng trên điện thoại/game thật**. Kiểm thử không dùng tài khoản hoặc đăng nhập game.
- Xác minh áp dụng so các biểu tượng trang bị, phù hiệu, phụ trợ đang thấy và tổng cấp ngọc. Bảng đề cử không hiển thị thành phần từng viên ngọc, nên không khẳng định đã kiểm tra độc lập thành phần ngọc ẩn.
- OCR không rõ, tải chậm, lỗi kết nối, tên người đề cử không phân biệt được, thay đổi danh sách, hoặc không chứng minh được biên danh sách đều làm dừng với lý do. **Không** coi màn hình trống/không đọc được là không có đề cử.
- Tướng chưa sở hữu vẫn được chọn trong danh sách đầy đủ nếu game cho phép. Nếu game mở UI mua/không khả dụng thay vì Chuẩn bị, macro dừng; không bấm mua/nhận và không ghi trạng thái “không có Pro”.
- Chạm Dùng combo và trả top 1 không tự thử lại khi Android báo hủy. Điểm đã hiệu chỉnh phải nằm trong nút của đề cử đang nhận diện, kể cả khi thử trực tiếp trong Chỉnh từng bước. Đường vuốt phải giữ hướng, vùng và độ dài có ý nghĩa. Các kiểm tra cửa sổ, đúng game, vùng an toàn, Stop/Pause cũ vẫn giữ nguyên.
- Ảnh chụp giữ nguyên màn hình theo yêu cầu, không tự cắt/che. UID không được trích xuất riêng cho tính năng này. Ảnh chỉ lưu cục bộ theo cơ chế thu thập hiện có; không dùng nút Lưu ảnh trong game. Ảnh người dùng gửi không đưa vào repo.
