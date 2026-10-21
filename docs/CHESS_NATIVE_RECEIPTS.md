# Ảnh chiêu Cờ Liên Quân: xác minh file và tránh tải lại

## Lỗi được xử lý

Một lần OCR trả `UNKNOWN` sau nhấn giữ không chứng minh rằng game chưa lưu ảnh. Bản trước chỉ ghi tiến độ khi đọc rõ chuỗi Save Photo → Sure → màn chi tiết; ảnh thực đã xuất có thể bị bỏ sót tiến độ, và Tiếp tục có thể nhấn giữ lại.

## Hai loại bằng chứng độc lập

- Màn hình: chỉ xác nhận Sure trong đúng Save Photo. Ảnh tham chiếu của chính trang chi tiết hiện tại cho phép nhận lại màn không đổi khi không đọc được tiêu đề. Tên tướng khác, prompt, ảnh thay đổi, thiếu OCR hoặc mất cửa sổ chi tiết vẫn chặn thao tác. File tồn tại không cho phép vuốt trên màn không rõ.
- File: trước nhấn giữ, lưu danh sách định danh ở cây Pictures đã cấp quyền. Sau thao tác, chỉ nhận đúng một ảnh mới có mốc thời gian phù hợp, đọc/giải mã đầy đủ và nội dung khớp biểu tượng. Ảnh cũ, screenshot, ảnh không liên quan, nhiều ảnh khớp, dữ liệu đang ghi hoặc thiếu thời gian không được coi là thành công. Không dùng tăng số lượng ảnh làm bằng chứng.
- Biểu tượng có thể được thăm dò lệch tâm: lấy tối đa ba vùng nhỏ có chi tiết trong phạm vi hẹp quanh điểm dò; so nội dung và tương quan cấu trúc với ảnh gốc gần vuông. Đây là đối chiếu bảo thủ, không phải nhận diện danh tính người.

## Tạm dừng và tiếp tục

- Lần chuẩn bị chưa gửi thao tác có thể được chuẩn bị lại an toàn. Lần đã ghi ý định gửi thao tác được giữ lại qua tạm dừng hoặc tiến trình bị đóng; không mặc định là thất bại rồi nhấn giữ lại.
- Khi tìm được ảnh, ghi dấu hoàn tất và ghim đúng định danh nguồn cùng SHA-256/số byte trong một lần ghi tiến độ. Sao chép vào lượt thu thập kiểm tra lại nội dung nguồn và bản sao; không đổi gán sang tướng khác vì ảnh khác xuất hiện muộn.
- Vị trí đã xác minh không có Save Photo được nhớ riêng, không ghi giả là đã lưu. Khi quay lại, đối chiếu ảnh xuất hiện muộn và xác minh lại màn chi tiết; không nhấn giữ trùng vị trí chỉ để kiểm tra.
- Mỗi giao dịch vẫn có giới hạn 15 giây; dừng/hủy không cho phép callback muộn gửi thao tác mới. Pictures được đọc ngoài luồng giao diện. Các bảo vệ cửa sổ, vùng chạm, shield và khoảng hiển thị Stop/Pause được giữ nguyên.

## Giới hạn và kiểm thử

- Lượt cũ không có nhật ký từng thao tác không thể suy ra chính xác ảnh nào thuộc lần lỗi chỉ từ số ảnh tổng hoặc ảnh gallery. Không tự đánh dấu hàng loạt ảnh cũ.
- Provider thiếu timestamp, quyền Pictures thay đổi, file chưa ghi xong, nội dung không khớp rõ hoặc màn hình chưa xác minh có thể vẫn cần tạm dừng. Thông báo phân biệt “chưa xác minh xong” với “chưa lưu”.
- Kiểm thử hồi quy gồm định danh cũ/mới/đã gán, mốc thời gian, hai ảnh khớp, ảnh chưa đọc được, nội dung khác, khôi phục nhật ký, prepared/pending/absent, ghim file, hình học điểm dò lệch tâm và ảnh tham chiếu bị thay đổi/tên tướng mâu thuẫn.
- Có kiểm tra thuật toán trực tiếp trên các pixel bằng chứng người dùng cung cấp ở ngoài repo. Ảnh đó không đưa vào source hoặc fixture. Ảnh gallery thu nhỏ không thay thế kiểm chứng file xuất thật trên thiết bị.
- Chưa xác nhận trên game/điện thoại thật; kiểm thử JVM và CI Android không được mô tả như một lượt game thật.
