# Cờ Liên Quân – Khắc phục tự dừng Save Photo sau 15 giây

## Lỗi trên điện thoại

Thông báo **Lưu ảnh gốc quá 15 giây. Kiểm tra Save Photo rồi bấm Tiếp tục.** xuất hiện giữa bảng kỹ năng tướng (ví dụ Mị Ảnh Dưới Trăng). Nguyên nhân: tổng thời gian cố định 15 giây trong nativeSave, bao gồm cả Android chụp ảnh, OCR và đối chiếu dữ liệu Pictures, không chỉ thời gian chờ game.

## Cách sửa

- NativeSaveWatchdog có idle 36 giây cho OCR/gesture, idle 70 giây cho xác minh file Pictures và tối đa 150 giây cho toàn giao dịch. Các giới hạn này không dùng 15 giây cố định.
- Chỉ tiến triển thực từ callback của cử chỉ, chụp/OCR, nhật ký, hoặc đối chiếu ảnh mới được cập nhật thời điểm phản hồi. Một timer lặp không được coi là tiến triển.
- Nếu tác vụ chậm quá 8 giây, lớp che liên tục được gỡ; nút Dừng/Tiếp tục hiện lại. Các thao tác grab và touchGame vẫn tự che overlay trong lúc chụp và chạm.
- Khi thực sự quá hạn, khôi phục overlay rồi hoàn thành callback thất bại theo đúng reader. Không gọi pause() trước khi dọn tài nguyên.
- Không tự bấm Save Photo/Sure lần hai khi kết quả chưa xác minh. Giữ tiến độ GamePhotoSession và quy tắc kiểm tra file/biên nhận hiện có.

## Kiểm thử

- NativeSaveWatchdogTest: vượt 15 giây nhưng vẫn có phản hồi không dừng; treo OCR 36 giây được phát hiện; đối chiếu Pictures được chờ dài hơn; hard limit 150 giây; đồng hồ uptime đổi nhẹ.
- Các kiểm thử chess reader, biên nhận ảnh và sao chép ảnh gốc vẫn giữ. Build, unit tests, UI/OCR và API 34 đều bắt buộc.
- Bản 0.13.27 code 58 kế thừa 0.13.26 của nhánh Chiến khu, cùng application ID, icon launcher và khóa ký.

## Thử thực tế

Trên màn hình Bách khoa Cờ Liên Quân, bấm Tiếp tục từ vị trí cũ. Nếu đã lưu ảnh gốc mà lần trước dừng khi chờ biên nhận, nên đối soát trạng thái Pictures trước, tránh mở lượt mới và làm ảnh trùng. Khi giao diện game khác mẫu hoặc OCR không đủ tin cậy, chương trình báo lỗi rõ thay vì tự đánh dấu hoàn thành.

CI giả lập không thay thế kiểm tra Save Photo trong game trên điện thoại thật.
