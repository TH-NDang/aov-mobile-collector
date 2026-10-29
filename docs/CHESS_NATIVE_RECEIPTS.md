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
- Mỗi giao dịch có watchdog theo tiến triển và giới hạn tổng 150 giây; dừng/hủy không cho phép callback muộn gửi thao tác mới. Pictures được đọc ngoài luồng giao diện. Các bảo vệ cửa sổ, vùng chạm, shield và khoảng hiển thị Stop/Pause được giữ nguyên.

## Giới hạn và kiểm thử

- Lượt cũ không có nhật ký từng thao tác không thể suy ra chính xác ảnh nào thuộc lần lỗi chỉ từ số ảnh tổng hoặc ảnh gallery. Không tự đánh dấu hàng loạt ảnh cũ.
- Provider thiếu timestamp, quyền Pictures thay đổi, file chưa ghi xong, nội dung không khớp rõ hoặc màn hình chưa xác minh có thể vẫn cần tạm dừng. Thông báo phân biệt “chưa xác minh xong” với “chưa lưu”.
- Kiểm thử hồi quy gồm định danh cũ/mới/đã gán, mốc thời gian, hai ảnh khớp, ảnh chưa đọc được, nội dung khác, khôi phục nhật ký, prepared/pending/absent, ghim file, hình học điểm dò lệch tâm và ảnh tham chiếu bị thay đổi/tên tướng mâu thuẫn.
- Có kiểm tra thuật toán trực tiếp trên các pixel bằng chứng người dùng cung cấp ở ngoài repo. Ảnh đó không đưa vào source hoặc fixture. Ảnh gallery thu nhỏ không thay thế kiểm chứng file xuất thật trên thiết bị.
- Chưa xác nhận trên game/điện thoại thật; kiểm thử JVM và CI Android không được mô tả như một lượt game thật.


## v0.13.30: đợi ảnh tải thật, chờ Pictures và báo đúng nguyên nhân

- Lỗi `save mark write failed` trước đây được truyền cả khi đoạn ghi dấu không thất bại, che mất nguyên nhân đối chiếu file. Bản này chỉ dùng lý do đó cho lỗi ghi thật; đối chiếu ghi số lượng ứng viên mới/trong cửa sổ/khớp/chưa đọc được/mơ hồ/thiếu thời gian, không ghi tên ảnh riêng tư.
- Đối chiếu tuần tự tối đa 30 giây và 16 lượt, mỗi lượt cách 2 giây, thay cho ba lần đọc cách 600 ms. Tiếp tục lần đang dở cũng chỉ đối chiếu, không nhấn giữ/Sure lại. Stop và callback muộn không được phép tiến thêm.
- Trước Sure lưu một mốc ý định duy nhất; hạn timestamp được tính từ mốc đó thay vì hết hạn khi OCR mở prompt còn chậm. Mốc không đổi khi polling, tạm dừng hoặc khởi động lại. Sau ghi nhật ký phải đọc lại prompt mới trước khi chạm. Baseline, nội dung, một ứng viên duy nhất và xác minh màn chi tiết vẫn bắt buộc.
- Lỗi ghi nhật ký thật vẫn không được bỏ qua: ảnh nguồn có thể tồn tại nhưng chưa được đánh dấu. Đối chiếu lại cùng attempt có thể hoàn tất khi lưu trữ phục hồi, không tải lại ảnh.
- Kiểm thử thêm: file nguồn tồn tại nhưng journal `.part` không ghi được; khôi phục cùng attempt không tạo ảnh thứ hai; prompt chậm hơn 20 giây, deadline không đổi qua resume; Pictures xuất hiện muộn, cancel/callback trùng/mất callback; chẩn đoán không bịa lỗi ghi.
- Log và ảnh Hayate chưa có dữ liệu Pictures/journal gốc nên không đủ kết luận lỗi cụ thể là timestamp, ảnh chưa lập chỉ mục hay chữ ký không khớp. Đây là sửa các lỗi kiểm soát đã xác minh trong mã, không tuyên bố đã chạy thành công game trên điện thoại.

### Popup tải chậm / giật

- Trước ghi ảnh chi tiết, tạo fingerprint hoặc dò Save Photo, chờ các khung chi tiết ổn định. Trang đầu quân cờ phải có biểu tượng có texture và chữ ký ảnh hợp lệ; nền trống/loading không đủ. Jitter hoặc nội dung thay đổi đặt lại chuỗi ổn định, không chụp/lướt tiếp ngay.
- Chờ có giới hạn 30 giây, đọc tuần tự và kiểm tra hủy; không dùng một khoảng sleep cố định để giả định đã tải. Trang cuộn sau có thể hợp lệ khi không có icon, nhưng vẫn phải ổn định. Hết hạn ghi rõ readiness và dừng, không đoán ảnh.
- Ngay trước nhấn giữ, đối chiếu lại ảnh chiêu với tham chiếu qua ba lần đọc mới; sau ghi journal đọc thêm khung mới trước gửi gesture. Nếu ảnh dịch/chuyển, không nhấn. Chỉ khi biết chắc chưa gửi gesture mới hủy attempt để lần sau chuẩn bị lại; nếu ghi hủy thất bại vẫn giữ trạng thái chưa rõ, không bỏ qua lỗi.
- Kiểm thử synthetic bổ sung placeholder→ảnh thật muộn, một khung ảnh thoáng qua, jitter rồi ổn định, timeout và hủy; không đưa ảnh người dùng vào repo. Đây không thay thế kiểm chứng popup game trên điện thoại thật.

### Nhấp nháy dòng “Ảnh gốc” của popup app

Đã xác minh `CaptureProgressPanel.refresh()` trước đây ghi dòng trạng thái cơ bản trước, rồi callback nền thay bằng trạng thái có thêm dòng “Ảnh gốc” mỗi lượt cập nhật. Việc bỏ/thêm dòng gây thay đổi chiều cao lặp lại; không phải bằng chứng game xuất ảnh chậm. Bản này giữ snapshot đầy đủ trong lúc đọc nền, chỉ cập nhật khi nội dung đổi và không mang trạng thái lượt cũ sang lượt mới. Bảo vệ ẩn overlay khi chụp vẫn giữ nguyên; không tắt shield để che triệu chứng.

## v0.13.31: căn chỉnh nội dung, không nới bằng chứng

Log ngày 28/10 có `signature=true, fresh=1, inWindow=1, matched=0, unreadable=0, ambiguous=0, undated=0, outsideWindow=0`. Do đó lần này thất bại ở đối chiếu nội dung của một ứng viên mới đọc được, không phải chờ lập chỉ mục hay lỗi quyền đọc.

Từ screenshot Hayate và ảnh chiêu riêng 128×128 người dùng gửi, chữ ký dựng lại làm matcher cũ trả false trên JVM thật: lưới scale 5% / vị trí 1/8 bỏ lỡ căn chỉnh phù hợp. Lân cận mịn tìm được phép so với sai số RGB 17.61, 90% ô gần nhau và tương quan 0.93263. Ngưỡng vẫn lần lượt ≤18, ≥85%, ≥0.92; không giảm độ tin cậy để nhận ảnh. Chưa có journal thực tế của lượt lỗi, nên đây là cơ chế lỗi tái hiện trên cặp ảnh được cung cấp, không tuyên bố đã chạy qua Hayate trên điện thoại.

- Giữ tìm thô trước; chỉ giữ tối đa bốn seed cho mỗi vùng chữ ký và tìm mịn trong lân cận hữu hạn về scale/vị trí. Không tìm tùy ý toàn ảnh ở mọi mức zoom. Số phép so có trần và được báo trong chẩn đoán.
- Chỉ ảnh mới trong cửa sổ attempt mới được giải mã/so; 1591 ảnh baseline không phải 1591 lượt căn mịn. Kiểm tra source, duy nhất một kết quả, nội dung ảnh, timestamp, SHA/bytes khi ghim, lưu journal bền vững và chống tải trùng vẫn giữ nguyên.
- Khi chưa khớp, log thêm kích thước, sai số RGB, phần ô gần nhau, tương quan và số phép so; tối đa ba bản tóm tắt số học, không ghi tên/path/ID ảnh riêng tư.
- Kiểm thử dùng hình tổng hợp lệch scale/vị trí và ảnh tương tự nhưng sai; ảnh người dùng chỉ dùng kiểm chứng cục bộ, không đưa vào repo/fixture. JVM cục bộ và emulator CI không thay thế kiểm chứng game trên điện thoại thật.


## v0.13.32: sửa lưới vị trí còn quá thưa với ảnh 128×128

- Log 29/10 cho thấy một file mới trong cửa sổ, đọc được, không mơ hồ; phép so tốt nhất cũ báo RGB 15.4367, tỷ lệ ô gần màu 0.83, tương quan 0.9295. Điều kiện 0.85 chưa đạt nên dừng bảo thủ. Log không chứa pixel gốc, vì vậy không đủ chứng minh file ấy là ảnh đúng.
- Lưới v0.13.31 dịch nửa bước theo phần lề còn lại của crop: với icon 128px, các vị trí vẫn có thể cách vài pixel. Bản này thêm tìm kiếm cục bộ theo pixel, bốn mức giảm dần vị trí/kích thước, tối đa ba seed mỗi vùng tham chiếu và tổng 972 phép so bổ sung. Chỉ vùng có nội dung gần khớp mới vào bước này; không quét vô hạn hoặc nới ngưỡng.
- Vẫn cần RGB ≤18, tỷ lệ ô gần màu ≥0.85, tương quan ≥0.92 cùng một phép so. Chẩn đoán chọn phép so theo điều kiện yếu nhất thay vì chỉ sai số RGB nhỏ nhất, và ghi số phép `fine` để phân biệt đường khớp mới.
- Giữ định dạng chữ ký `native-icon-rgb-v1`, baseline, mốc Sure, thời hạn và journal cũ. Tiếp tục lượt đang dở sẽ đối chiếu file đã có bằng thuật toán mới; không nhấn giữ hoặc Sure lại khi receipt chưa rõ. Nhiều file khớp, file chưa đọc được, sai thời gian hoặc nguồn đã gán vẫn bị chặn.
- Kiểm thử mới dùng ảnh tổng hợp 128×128 và crop phân số, gồm trường hợp thuật toán cũ thất bại; ảnh đảo/lật/đổi màu/nền trơn và receipt trùng/ngoài cửa sổ vẫn bị từ chối. Không đưa ảnh người dùng vào repository. Không tuyên bố lỗi Hayate trên điện thoại đã được xác nhận hết nếu chưa có kiểm thử thực tế.
