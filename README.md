# AOV Mobile Collector — v0.3

App thử nghiệm Android 11+ để chụp dữ liệu menu Liên Quân VN bằng macro. Ảnh lưu trên máy, OCR chạy ngoại tuyến; app không có quyền Internet.

## Cách dùng
1. Bấm **Trợ năng** trên header và bật AOV Collector trong cài đặt hệ thống.
2. Bấm **Bảng nổi**, mở game thủ công và vào màn hình tướng.
3. Mở danh sách macro trên bảng nổi; tìm tên có dấu hoặc không dấu.
4. Chọn **Một tướng đang mở** hoặc **Danh sách tướng**; nhập tên tùy chọn hoặc số ô cần thu thập.
5. Bấm **Chạy**. Nút đổi thành **Tạm dừng**, rồi **Tiếp tục**. **Dừng** kết thúc lượt và giữ ảnh.
6. **Chụp** lưu ảnh thủ công. **Về app** tạm dừng và mở lịch sử. **Đóng** tạm dừng và ẩn bảng. Dấu **−** thu gọn, vẫn chạy.
7. Trong lịch sử, mở thư mục để xem ảnh; bấm **Chọn** hoặc nhấn giữ để chọn nhiều, **Lưu ZIP** hoặc **Xóa**. Menu từng mục có đổi tên. File ZIP lưu ra chứa các ZIP nhỏ dưới 450 MB, xếp theo chữ cái.
8. **Nhập** trên header nhận ZIP đã xuất (cả dạng ZIP chứa ZIP nhỏ lẫn ZIP cũ) hoặc macro JSON. Macro JSON xuất hiện trong ô tìm của bảng nổi.

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


## v0.4.1: macro cũ được nâng cấp, cuộn thích nghi

- **Lỗi của v0.4.0:** macro "Danh sách tướng" đã chọn từ bản cũ được lưu sẵn trong `macro.json` cùng các bước cũ. Cập nhật app không tạo lại macro này, nên lượt chạy vẫn vuốt một đoạn cố định và chạm theo tọa độ cố định: trượt quá, rồi chọn Enzo thay vì Bolt Baron. Ảnh `0595-page-02-list` là dấu hiệu: macro mới đặt số bước khác (0582).
- Macro dựng sẵn giờ có số phiên bản và được tạo lại với cùng thiết lập (tên, số ô) khi kết nối dịch vụ, khi tạo lượt mới hoặc khi bấm Chạy, trừ khi đang có lượt dở dang của macro đó.
- Nếu một lần vuốt trượt xa đến mức không đo được (ảnh trước và sau không còn phần chung), app vuốt ngược lại, đo so với ảnh trước, rồi dùng bước vuốt bằng một nửa cho các lần sau (thấp nhất một phần tư). Chỉ khi vẫn mất dấu mới quay về đầu danh sách để đếm lại. Bước vuốt tối đa giảm còn 0,45 màn hình và phạm vi đo được tăng lên khoảng 0,74 màn hình.
- Logic điều hướng nằm trong `ListNavigator`. Unit test chạy đúng logic này trên danh sách vẽ giả lập, gồm trường hợp vuốt trượt 1,5–2,3 lần (khi giữ tay không chặn được quán tính) và trường hợp bắt đầu giữa danh sách.


## v0.4.2: tạm dừng có lý do, đo thời gian, chạy nhanh hơn

- **Tạm dừng vô lý giữa lượt:**
  - Trước đây mọi sự kiện "cửa sổ thay đổi" từ app khác (thông báo, bàn phím, chế độ game hoặc tiết kiệm pin) đều làm lượt dừng ngay.
  - Mỗi bước app đều kiểm tra cửa sổ ở phía trước. Với game Unity, Android đôi khi không trả lời; trường hợp đó bị coi là "đã rời game".
  - Giờ app chỉ dừng khi một app khác thực sự nằm phía trước sau 1,5–2 giây. Không biết cửa sổ nào ở trước thì không còn bị coi là rời game.
  - Chụp màn hình lỗi được thử lại một lần.
- **Lý do tạm dừng:** hiện trên bảng nổi, kèm tên app đang ở phía trước và lỗi cụ thể (chụp màn hình, chạm bị hủy, quá thời gian…). Lý do cũng được ghi vào `events.txt` của lượt, xem trong lịch sử qua ⋮ → **Nhật ký lượt**.
- **Thời gian thu thập:** bảng nổi hiện "Tướng 23/129 · 25:12 · còn ~1:10:00". Đồng hồ không tính lúc tạm dừng. Tổng số tướng được đọc từ bộ đếm "14/129" trên danh sách. `run.json` lưu `activeMs`, lịch sử hiện "chạy 1:05:12".
- **Tất cả:** nút này giờ là 300 ô và luôn dừng ở cuối danh sách. Không cần biết trước số tướng.
- **Nhanh hơn, ảnh không đổi:**
  - Ảnh PNG được lưu ngầm, tối đa 2 ảnh chờ ghi; macro không còn đợi nén xong mới đi tiếp.
  - Thời gian nghỉ giữa các bước giảm từ 400 xuống 150 ms; chờ trước khi chụp giảm từ 350 xuống 250 ms.
  - Ước tính khoảng 45 giây mỗi tướng, so với khoảng 75 giây trước đây.
- **Tùy chọn trong form, mặc định tắt:**
  - **Chạy nhanh:** chờ 600 ms sau mỗi lần chạm, 450 ms khi chuyển tab Tóm tắt/Chi tiết; ước tính khoảng 36 giây mỗi tướng.
  - **Chỉ chụp Chi tiết:** bỏ 4 ảnh Tóm tắt; bật cùng Chạy nhanh thì ước tính khoảng 30 giây và 6 ảnh mỗi tướng.
  - Nên thử với 2 tướng rồi xem ảnh trước khi chạy cả danh sách.


## v0.4.3: tự quay về danh sách khi còn kẹt ở trang tướng

- **Lỗi:** một lượt dừng ở trang chi tiết của Edras với thông báo "Màn hình hiện tại không phải danh sách Tất cả tướng". Tướng trước đã chụp xong, nhưng game không nhận lần chạm nút quay lại của macro, nên bước chọn tướng kế tiếp vẫn thấy trang tướng.
- Giờ khi bước chọn tướng không thấy danh sách:
  - App chờ 1,2 giây rồi nhìn lại, phòng trường hợp danh sách đang hiện dần.
  - Nếu vẫn chưa thấy và góc trên trái có mũi tên quay lại "‹" của game, app bấm mũi tên đó, chờ 1,5 giây rồi tìm lại danh sách. Mỗi bước được bấm tối đa 3 lần.
  - Danh sách thường giữ nguyên vị trí cuộn, nên app so với ảnh danh sách trước đó và chạy tiếp mà không phải cuộn lại từ đầu.
- Mũi tên được nhận dạng theo hình dạng: 2 nét trắng hội tụ về một mũi nhọn. Màn hình chính của game không có mũi tên này, nên app không bấm bừa ở đó mà tạm dừng như trước.
- Mỗi lần bấm quay lại được ghi vào **Nhật ký lượt**. Ảnh màn hình lúc đó được giữ trong lượt với tên `…-not-list-1`, để biết game đang mở gì.
- Unit test thêm 4 trường hợp:
  - Game bỏ qua lần chạm quay lại ở 3 tướng trong danh sách 61 tướng; vẫn lấy đủ, đúng thứ tự.
  - Danh sách hiện chậm; app không bấm quay lại.
  - Đang ở màn hình chính; app không bấm gì.
  - Mũi tên được nhận ra trên ảnh cắt thật (danh sách, bảng kỹ năng, trang Edras), và không bị nhận nhầm trên tóc trắng, thanh tiền tệ hay thẻ tướng.


## v0.4.4: chạy nhanh hơn, chuyển thẳng giữa các chiêu, tiến trình lưu ZIP, dung lượng dễ đọc

- Mỗi tướng có khoảng 58 bước. Trước đây, sau mỗi bước app nghỉ thêm 150 ms, tổng khoảng 8,7 giây mỗi tướng. Khoảng nghỉ này thừa vì:
  - Mỗi cú chạm đã có bước chờ riêng ngay sau nó.
  - Mỗi cú chạm và mỗi lần chụp đều tự ẩn bảng nổi rồi chờ trước khi thao tác.
- Giờ bước kế tiếp chạy ngay khi bước trước xong. Thời gian chờ sau mỗi cú chạm (để game chuyển màn hình) giữ nguyên, nên ảnh chụp không đổi.
- Android chỉ cho dịch vụ trợ năng chụp 1 ảnh mỗi giây. Nếu lần chụp mới đến quá sớm, app chờ cho đủ 1,05 giây kể từ lần trước, thay vì để Android từ chối rồi thử lại sau 1,1 giây.
- Ước tính mỗi tướng, gồm cả chọn tướng trong danh sách:

| Chế độ | Trước | Sau | 129 tướng |
|---|---|---|---|
| Mặc định | ~45 giây | ~37 giây | ~1 giờ 20 phút |
| Chạy nhanh | ~36 giây | ~28 giây | ~1 giờ |
| Chạy nhanh + Chỉ chụp Chi tiết | ~30 giây | ~23 giây | ~50 phút |

- **Tùy chọn mới "Chuyển thẳng giữa các chiêu"**, mặc định tắt nên kịch bản gốc không đổi:
  - Khi bảng chiêu đang mở, app bấm luôn biểu tượng chiêu kế tiếp, không bấm đóng bảng rồi mở lại.
  - Bảng chỉ được đóng sau chiêu cuối, trước khi quay về danh sách.
  - Mỗi tướng bớt 3 lần đóng bảng cùng thời gian chờ sau mỗi lần.
  - Sau mỗi lần chuyển, app vẫn bấm tab Chi tiết, nên ảnh đúng tab dù game có giữ tab cũ hay không.
- Kịch bản bỏ ảnh Tóm tắt và chuyển thẳng giữa các chiêu: tick **Chỉ chụp Chi tiết** và **Chuyển thẳng giữa các chiêu**. Thêm **Chạy nhanh** thì ước tính khoảng 20 giây mỗi tướng, 129 tướng khoảng 45 phút.
- **Tiến trình lưu ZIP:**
  - Khi lưu ZIP, một hộp thoại hiện thanh tiến trình theo dung lượng, số file đã ghi / tổng số file, số MB, thời gian còn lại ước tính và file đang ghi.
  - Nút **Hủy** dừng ngay và xóa file ZIP dở.
  - Màn hình được giữ sáng trong lúc lưu.
  - Nhập ZIP cũng có hộp thoại này, tính theo phần file ZIP đã đọc. Hủy thì xóa phần đã nhập.
- **Lưu ZIP nhanh hơn:**
  - Ảnh PNG vốn đã nén. Nén lại trong ZIP không giảm dung lượng: đo trên ảnh thật vẫn 100%, mà tốn khoảng 37 MB/s CPU trên máy tính, trên điện thoại chậm hơn.
  - Giờ ảnh PNG được ghi thẳng vào ZIP, chỉ file JSON và nhật ký còn được nén. File ZIP vẫn mở bình thường bằng mọi công cụ giải nén.
- **Dung lượng dễ đọc:**
  - Mọi chỗ hiện dung lượng tự đổi đơn vị KB/MB/GB, dùng dấu phẩy thập phân, ví dụ "850 KB", "24 MB", "2,61 GB".
  - Tiến trình ZIP hiện theo đơn vị của tổng, ví dụ "0,85/2,61 GB".
  - Khi chọn mục trong lịch sử, app hiện tổng dung lượng đã chọn (file ZIP có dung lượng gần như vậy) và dung lượng máy còn trống. Màn hình lịch sử hiện tổng dung lượng ảnh đã lưu.
- **Ước tính trước khi chạy:** form chọn tác vụ không còn dòng cố định "Khoảng 1 phút và 25 MB mỗi tướng". Thay vào đó là ước tính cho cả lượt, tính lại khi đổi số ô hoặc tùy chọn, ví dụ "Ước tính: Tất cả 129 tướng · 1290 ảnh · khoảng 1 giờ 19 phút / Cần khoảng 1,8 GB · máy còn trống 21 GB".
  - Thời gian được tính từ chính các bước của macro.
  - Dung lượng mỗi ảnh lấy trung bình từ các ảnh đã lưu trên máy; chưa có ảnh nào thì dùng 1,5 MB.
  - Số tướng của "Tất cả" lấy từ bộ đếm danh sách đọc được ở lần chạy trước; chưa đọc được thì tạm tính 130.
  - Nếu không đủ chỗ trống, dòng ước tính chuyển màu đỏ.

## v0.4.5: ZIP chia phần dưới 450 MB, đọc được tên Qi

- **Lưu ZIP thành nhiều ZIP nhỏ:**
  - File ZIP lưu ra giờ chứa các ZIP nhỏ, mỗi ZIP tối đa 450.000.000 byte. Con số này dưới 450 MB theo cả hai cách tính (1 MB = 1.000.000 hay 1.048.576 byte).
  - Thư mục được xếp theo thứ tự chữ cái, không phân biệt hoa thường: các file của lượt (trang danh sách, `run.json`) trước, rồi từng thư mục tướng từ A đến Z. Trong mỗi thư mục, file xếp theo tên, tức theo thứ tự chụp.
  - Mỗi thư mục tướng nằm trọn trong một ZIP nhỏ. ZIP nhỏ đầy thì tướng kế tiếp sang ZIP mới. Chỉ thư mục lớn hơn 450 MB mới bị chia theo file.
  - Tên ZIP nhỏ gồm số thứ tự và tướng đầu, tướng cuối, ví dụ `01_airi_errol.zip`, `02_fennik_omega.zip`.
  - Thử trên bộ ảnh thật (129 tướng, 933 file, 1,32 GB): ra 4 ZIP nhỏ 420 MB, 426 MB, 421 MB và 84 MB. Giải nén bằng `unzip` và Python đều đúng từng byte, không tướng nào bị chia đôi.
  - Hộp thoại tiến trình hiện thêm ZIP nhỏ đang ghi, ví dụ "Phần 2 · file 340/933 · 0,61/1,32 GB".
- **Nhập ZIP:** đọc được cả ZIP mới (chứa ZIP nhỏ) lẫn ZIP xuất từ bản cũ. Các ZIP nhỏ được đọc lần lượt ngay trong file lớn, không cần giải nén ra máy trước.
- **Tên tướng Qi:**
  - Trước đây ML Kit không trả về chữ nào ở vùng tên của Qi. Tên chỉ có 2 chữ, nét mảnh, nằm trên nền nhiều chi tiết. Thư mục tướng vì vậy bị đặt là `tuong-112`, tên "Chưa rõ tên".
  - Kể cả khi vùng tên được đổi thành chữ đen trên nền trắng, ML Kit vẫn không trả về chữ nào: chữ quá ngắn nên bị bỏ qua. Cùng chữ đó nằm trong một dòng dài hơn thì được đọc.
  - Giờ nếu lượt đọc đầu không ra tên hợp lệ, app đọc lần hai như sau:
    - Tách tên ra thành chữ đen trên nền trắng. Tên là phần sáng nhất trong vùng: điểm ảnh sáng hơn mức giữa nền và chữ là tên.
    - Cắt sát quanh tên, rồi vẽ tên 3 lần trên một dòng, cách nhau một khoảng như giữa hai từ ("Qi Qi Qi").
    - Đọc dòng đó, rồi lấy chữ trong từng bản theo vị trí. Kết quả là cách đọc mà nhiều bản giống nhau nhất.
  - Tướng đã đọc đúng ở lượt đầu không bị ảnh hưởng.
  - Ảnh vùng tên thật của Qi được đưa vào bài kiểm tra trên máy ảo. App phải đọc ra đúng "Qi".
  - Lượt đã chụp trước đây vẫn giữ tên cũ; đổi tên `tuong-112` thành Qi trong menu ⋮ của thư mục.

## v0.4.6: lưu và nhập ZIP chạy nền, tiến độ trên thanh thông báo

- **Nút "Chạy nền"** trong hộp thoại lưu hoặc nhập ZIP: hộp thoại đóng lại, việc lưu vẫn chạy tiếp. Trong lúc đó vẫn xem ảnh được, hoặc rời app, tắt màn hình.
- **Thông báo tiến độ** hiện suốt lúc lưu hoặc nhập:
  - Tiêu đề có phần trăm, ví dụ "Đang lưu ZIP · 46%".
  - Có thanh tiến độ, dòng "Phần 2 · file 340/933 · 0,61/1,32 GB · còn khoảng 2 phút" và file đang ghi.
  - Nút **Hủy** ngay trên thông báo.
  - Bấm vào thông báo để mở lại hộp thoại tiến độ.
  - Thông báo cập nhật mỗi giây.
- **Khi xong:**
  - Nếu đang mở app, kết quả hiện trong app như trước.
  - Nếu không, kết quả đến bằng một thông báo, ví dụ "Lưu ZIP: Đã lưu ZIP gồm 933 file, chia thành 4 ZIP nhỏ…".
- **Lần đầu bấm "Chạy nền"** trên Android 13 trở lên, app xin quyền thông báo. Nếu từ chối, việc lưu vẫn chạy nền nhưng tiến độ chỉ xem được khi mở lại app.
- **Đang chạy nền mà bấm Lưu ZIP, Xóa hoặc Nhập:** app báo đang bận, kèm nút **Xem tiến độ**.
- **Để không bị Android dừng giữa chừng:**
  - Trong lúc lưu hoặc nhập, app chạy một dịch vụ nền có thông báo (loại đồng bộ dữ liệu).
  - App giữ CPU chạy cả khi tắt màn hình, tối đa 3 giờ mỗi lần, và nhả ra ngay khi xong.
  - Quyền mới: thông báo, dịch vụ nền, giữ máy thức. App vẫn không có quyền Internet.
- **Nếu đóng hẳn màn hình app trong lúc lưu,** app giữ quyền ghi vào file ZIP đã chọn đến khi xong. Nhờ vậy nút Hủy vẫn xóa được file dở.
- **Kiểm tra trên máy ảo:**
  - Một lượt lưu giả lập phải hiện thông báo có thanh tiến độ và vào chế độ dịch vụ nền.
  - Nút Hủy của thông báo phải hủy được.
  - Khi xong phải tắt dịch vụ và để lại thông báo kết quả.
