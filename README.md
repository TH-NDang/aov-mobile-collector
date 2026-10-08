# AOV Mobile Collector — v0.3

App thử nghiệm Android 11+ để chụp dữ liệu menu Liên Quân VN bằng macro. Ảnh lưu trên máy, OCR chạy ngoại tuyến; app không có quyền Internet.

## Cách dùng
1. Bấm **Trợ năng** trên header và bật AOV Collector trong cài đặt hệ thống.
2. Bấm **Bảng nổi**, mở game thủ công và vào màn hình tướng.
3. Mở danh sách macro trên bảng nổi; tìm tên có dấu hoặc không dấu.
4. Chọn **Một tướng đang mở** hoặc **Danh sách tướng**; nhập tên tùy chọn hoặc số ô cần thu thập.
5. Bấm **Chạy**. Nút đổi thành **Tạm dừng**, rồi **Tiếp tục**. **Dừng** kết thúc lượt và giữ ảnh.
6. **Chụp** lưu ảnh thủ công. **Về app** tạm dừng và mở lịch sử. **Đóng** tạm dừng và ẩn bảng. Dấu **−** thu gọn, vẫn chạy.
7. Trong lịch sử, mở thư mục để xem ảnh; bấm **Chọn** hoặc nhấn giữ để chọn nhiều, **Lưu ZIP** hoặc **Xóa**. Menu từng mục có đổi tên.
8. **Nhập** trên header nhận ZIP đã xuất hoặc macro JSON. Macro JSON xuất hiện trong ô tìm của bảng nổi.

Nền bảng nổi bán trong suốt, có thể kéo ở thanh tiêu đề. Bảng tự ẩn khi chụp để không dính vào ảnh. Xuất ZIP trước khi gỡ app vì gỡ app sẽ xóa ảnh nội bộ.

## Build
Android SDK 35, JDK 17, Gradle 8.9:
```
base64 --decode debug-keystore.base64 > collector-debug.keystore
gradle assembleDebug lintDebug
```
Mở thư mục bằng Android Studio hoặc chạy workflow; Gradle wrapper chưa được đóng gói.

## Giới hạn
Screenshot cần API 30+, có thể thất bại nếu game chặn chụp. Nút Dừng hủy các bước tiếp theo; một gesture/screenshot đã gửi Android có thể vẫn hoàn thành. Tạm dừng khi bước đang chạy sẽ chờ hoàn tất bước đó rồi lưu checkpoint. Nếu app bị kill giữa một bước, bước đó có thể chạy lại khi tiếp tục. Thay đổi UI/độ phân giải/tỉ lệ màn hình có thể làm tọa độ lệch; chuẩn hóa tỉ lệ không thay thế hiệu chỉnh. Một popup hệ thống hoặc rời game sẽ tạm dừng. Không có bảo đảm phát hiện mọi popup Unity.


## v0.2: collections and file management

- Built-in single-hero collection: open hero detail, enter a label, prepare and Run.
- Batch collection: start at the top of All heroes, five columns and two full rows on a 2400x1080 landscape screen. Try two entries first, then configure the count (the supplied screenshot shows 129 entries, including alternate forms). Pages use a slow two-row swipe. Scrolling and final-page alignment need real-device verification; duplicates and misses are possible. This is a bounded coordinate macro, not OCR or an end-of-list detector. Heroes with more than four skill icons require separate capture.
- Browse collections with paged thumbnails, open images, delete an image or a collection after confirmation, export individual collections, and restore captures from an exported ZIP.
- Pause completes the in-flight action and retains the checkpoint. Run resumes at the next step with the game in its preserved state. Skip skips exactly one action after confirmation. Stop ends the session; photos remain, and a new session is required. Gestures already dispatched to Android may finish.
- Updating v0.1 may require uninstall because its CI signing key was ephemeral. Export and verify the old ZIP first, then install v0.2 and restore the ZIP. v0.2 CI did not use the intended key, so moving from v0.2 to v0.3 may require export, uninstall, install and ZIP restore. Starting in v0.3, Gradle explicitly selects the public development-only signing key; do not use it for production releases.


## v0.3: floating task picker and library UI

The main screen is collection history, with Accessibility, Import and Floating panel actions in the header. Tap a folder to browse, long-press or choose Select for multiple items, then Save ZIP or Delete. Menus also support renaming. Import handles nested hero folders as well as v0.1/v0.2 ZIPs.

Choose Single hero, Hero list or an imported JSON macro directly on the floating controller. Name and count inputs are available over the game. Collapse keeps the task running; closing the panel pauses it. End retains captured images and requires a new session. The searchable dropdown includes imported JSON macros. The controller has Run/Pause/Resume, Stop, Capture, Back to app and Close controls on a translucent background.

Bundled ML Kit reads the hero-name region from overview screenshots. A valid-looking result is used as a folder name, with suffixes to prevent overwrites. No readable name means a numbered folder; the raw OCR value and its source are saved in hero.json, and users can correct names in history. Recognition is not validated against a complete roster and can misread stylized fonts. Coordinates still target the supplied 2400x1080 layout. This changes naming, not batch completeness guarantees.


## v0.3.2: chạm xuyên bảng nổi và khôi phục khi bị gián đoạn

- Lỗi chính của v0.3.1: khi macro chạm vào game, bảng nổi chỉ được ẩn hình, cửa sổ của nó vẫn nhận cú chạm thêm vài khung hình. Cú chạm nằm dưới bảng (biểu tượng chiêu, nút Chi tiết…) rơi vào nút ẩn của bảng thay vì vào game: ảnh chiêu/thuộc tính bị trùng ảnh tổng quan, và có lúc bấm nhầm **Đóng**/**Tạm dừng** làm mất bảng nổi giữa lượt.
- Từ v0.3.2, trước mỗi lần chạm/vuốt bảng được ẩn và đặt không nhận chạm, chờ 150 ms rồi mới gửi thao tác. Nếu cú chạm vẫn rơi vào bảng, bảng nuốt cú chạm (không bấm nút nào) và macro gửi lại, tối đa 2 lần; chạm bị Android hủy cũng được gửi lại. Thời gian giữ một cú chạm tăng từ 70 lên 100 ms.
- `run.json` có thêm `panelTapRetries`: số cú chạm đã rơi vào bảng nổi và phải gửi lại. Giá trị lớn hơn 0 xác nhận lỗi trên đúng máy đó; nếu bằng 0 mà ảnh vẫn trùng thì nguyên nhân nằm ở tọa độ/thời gian chờ của game.
- Mỗi bước có giới hạn thời gian; bước treo sẽ tạm dừng lượt thay vì kẹt ở "Đang xử lý". Lỗi ghi file/ảnh tạm dừng lượt thay vì làm sập dịch vụ.
- Nếu dịch vụ bị dừng khi đang chạy, lần kết nối lại bảng nổi tự mở với lượt ở trạng thái tạm dừng. Android thường tắt Trợ năng của dịch vụ bị lỗi: vào **Bật Trợ năng**, tắt rồi bật lại AOV Collector.
- Lỗi và gián đoạn được ghi vào nhật ký trên máy. App hiện nhật ký khi mở lại sau sự cố; **Hướng dẫn → Nhật ký lỗi** cho phép xem và sao chép để gửi khi báo lỗi.
- Build tách APK theo loại chip: `app-arm64-v8a-debug.apk` (khoảng 22 MB, dùng cho hầu hết điện thoại) và `app-universal-debug.apk` (khoảng 50 MB, chạy trên mọi máy). Trên GitHub Actions là hai mục `aov-mobile-collector-apk-arm64` và `aov-mobile-collector-apk`.
- Mở danh sách macro không còn đẩy bảng nổi lên đầu màn hình vĩnh viễn; bảng trở về vị trí cũ khi đóng danh sách. Chọn tác vụ mới không còn đổi trạng thái lượt đã hoàn tất thành "đã kết thúc". Tên OCR bỏ ký tự thừa ở đầu/cuối (". Flowborn" → "Flowborn").


## v0.4.0: cuộn danh sách có đo và giao diện lịch sử

- **Danh sách tướng không còn lặp hoặc bỏ sót khi cuộn.** Bản cũ vuốt một đoạn cố định rồi chạm theo tọa độ cố định. Game vẫn trượt tiếp sau khi thả tay, còn ở cuối danh sách thì cuộn ít hơn (hàng cuối lẻ), nên lệch hàng và lấy lại tướng ở hàng trên. Bước `pick` mới:
  - Lần đầu tự hất danh sách về đầu và kiểm tra rằng nó không còn cuộn được nữa.
  - Vuốt từng đoạn rồi giữ tay 0,45 giây trước khi thả để danh sách không trượt thêm.
  - So ảnh chụp trước và sau mỗi lần vuốt để đo chính xác số pixel đã cuộn, rồi tính vị trí thẻ từ số đo đó thay vì giả định.
  - Trước mỗi tướng, đối chiếu với ảnh lần trước. Nếu màn hình không còn là danh sách (ví dụ nút quay lại không ăn), lượt tạm dừng với hướng dẫn; khi chạy tiếp, app tự về đầu danh sách và đếm lại.
  - Ô không có thẻ, hoặc danh sách không cuộn thêm được, nghĩa là đã hết danh sách: lượt kết thúc với "Đã hết danh sách". Chọn **Tất cả** (200) để lấy đến cuối.
  - Ảnh `page-XX-list` được lưu sau mỗi lần cuộn để kiểm tra lại.
- Kiểm thử: unit test giả lập danh sách 7, 61 và 129 tướng với độ trượt ngẫu nhiên tới 30%; mỗi tướng được chạm đúng một lần theo thứ tự và lượt dừng đúng ở cuối. Các ngưỡng đo được hiệu chỉnh trên ảnh danh sách thật 2400×1080 (thẻ, kể cả thẻ tướng chưa có, so với nền trống).
- **Lịch sử:**
  - Chọn mục, vào thư mục rồi quay ra không còn nhảy lên đầu: vị trí cuộn được nhớ theo từng thư mục. Ảnh thu nhỏ được giữ trong bộ nhớ đệm.
  - Xem ảnh: vuốt ngang hoặc bấm ‹ › để sang ảnh khác cùng thư mục, có dải ảnh nhỏ bên dưới, kèm số thứ tự (ví dụ 3/10).
  - Mỗi thư mục hiện ngày giờ tạo. Đổi tên có ô **Thêm mốc thời gian**, mặc định bật cho lượt thu thập (ví dụ "Đợt thử · 08/10 21:48").
