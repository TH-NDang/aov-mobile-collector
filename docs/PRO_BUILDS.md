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

## v0.13.19: phân biệt thao tác bị chặn và thao tác đã gửi

Hai lỗi cũ “Không thực hiện được thao tác Chọn tướng” và “Không cuộn được danh sách Pro” chỉ chứng minh callback thất bại, không chứng minh game đã nhận cú chạm, OCR sai hay cửa sổ nào cản. Không có ảnh/lý do mới từ máy thật thì chưa kết luận nguyên nhân thiết bị.

Lỗi mới có ID thao tác (ví dụ `pro.v1.picker.open`, `pro.v1.list.scroll`), mã/lý do cửa sổ, và số phần đã được Android nhận. Nếu phần vuốt đã gửi nhưng phần giữ bị chặn, báo có thể đã thay đổi màn hình; không tự phát lại. Ngoại lệ dispatch hoặc thiếu bằng chứng báo chưa rõ, không khẳng định “chưa gửi”. `events.txt` lưu thêm profile, điểm mặc định/hiệu lực, nguồn điểm (mặc định hoặc giống mặc định / hiệu chỉnh khác mặc định), và hình học cửa sổ ẩn danh. Không ghi tên gói ứng dụng khác, chữ OCR, UID, mật khẩu hoặc ảnh vào chẩn đoán này.

Một lỗi nguồn đã tái hiện: hiệu chỉnh lưu không đạt vùng an toàn bị chặn trước cả khi mở bảng chỉnh. Nay chỉ bỏ kiểm tra đó ở bước **mở xem trước**, còn **Chạy bước / Tiếp** vẫn kiểm tra điểm thực tế. Có thể sửa điểm hoặc bấm **Mặc định** cho đúng thao tác, không cần cấu hình lại tài khoản hay các mục khác.

Giới hạn giữ nguyên: đường cuộn Pro lớn (`.22`) và dò nhỏ (`.018`) dùng chung ID; hiệu chỉnh tương đối hai đầu có thể hợp lệ ở đường lớn nhưng làm đường nhỏ quá dài. App không âm thầm bỏ qua, co giãn hay di chuyển hiệu chỉnh đã lưu. Khi gặp trường hợp này, dừng có lý do rõ và cho sửa/reset ở bước hiện tại. Dịch cả hai đầu cùng khoảng giữ nguyên độ dài tốt hơn thay đổi riêng một đầu, nhưng vẫn phải qua kiểm tra an toàn.

Kiểm thử đơn vị thuần cho bộ kiểm tra điểm và định dạng chẩn đoán bao gồm hiệu chỉnh đường lớn hợp lệ/đường nhỏ bị chặn nhưng vẫn mở chỉnh; điểm chưa sửa không được chạy; phân biệt từ chối cửa sổ trước dispatch, hủy sau khi đã nhận, lỗi phần giữ sau phần vuốt, ngoại lệ chưa rõ, giới hạn độ dài báo cáo và không dùng lại lý do cũ. Bộ `ProGestureAttemptTest` chạy đúng đường adapter dùng trong CollectorService để kiểm tra mở xem trước với điểm lưu bị chặn, từ chối runtime, reset hợp lệ, và lỗi chi tiết kết thúc lượt trước callback lỗi chung. `TeachingDeviceSmoke` bổ sung thao tác trên ứng dụng giả đã xác minh: hiệu chỉnh đường lớn không hợp lệ cho đường nhỏ vẫn mở bảng, Chạy báo T09 không gửi input, Mặc định rồi Chạy gửi đúng một đường vuốt thật. Các ca mới cần chạy trong CI Android; không dùng ảnh hay tài khoản người dùng. Các kiểm tra đọc danh sách/top 5/ít hơn/trống/khôi phục top 1 và bảo vệ cửa sổ giữ nguyên. Kiểm thử tổng hợp Android không thay thế xác nhận trên điện thoại/game thật.


## v0.13.20: bounded overlap recovery

A missing complete-card overlap is not permission to advance rank. The reader now re-observes the same viewport twice without gestures. If overlap remains unproved, discovery can restart at a positively verified top with coarse step caps 0.11, 0.055, 0.0275, then 0.018 (four restarts maximum). Fine one-card scans remain 0.018. No apply/capture occurs during discovery or recovery.

The already observed ordered prefix is retained separately across restarts. Every replayed rank must match its original creator and all twelve component signatures; the original descriptor is retained, not overwritten by successive fuzzy observations. A changed, skipped, duplicated or shortened prefix cannot become a successful new catalog. The complete catalog still freezes only after positive end proof, including the larger end-motion probe. The first five original ranks and exact original top-1 restoration remain unchanged. Pending applies remain observation-only; there is no blind apply retry.

The fixed list-motion signature no longer samples the right-side attribute tooltip. Top search additionally requires displacement of a shared identified card (or the existing positive short/empty-list two-way proof), not merely changed pixels. Existing dispatch diagnostics, calibrated-path validation, account/window checks and stop handling remain intact; saved calibration is not silently discarded.

Evidence limit: the reported down/up/up/up/down sequence is consistent with the existing top search followed by first discovery. The supplied image shows a complete middle card, clipped neighbors and a tooltip. Nominal 0.22 movement should retain the second complete card, which is covered by a geometry regression. Without the preceding OCR/pixel frames, that image alone does not establish whether descriptor jitter, OCR instability or actual movement caused the missing overlap. This release adds safe recovery and fixes the independently reproducible tooltip-motion contamination; it does not claim real-device collection has already passed.

Regressions cover transient descriptor mismatch, persistent mismatch with bounded termination, oversized movement followed by verified replay, changed frozen ranks, an empty replay after a nonempty prefix, cancellation, nominal clipped-card geometry, tooltip exclusion, complete/fewer/empty catalogs, same-creator distinct combos, full-catalog ambiguity and pending-apply/capture recovery. User-provided screenshots are not included in the repository.


## v0.13.33: tách lỗi đọc thẻ khỏi lỗi cuộn về đầu

Ảnh báo lỗi 29/10 hiển thị hai thẻ và nút Dùng combo; biên đáy thẻ đủ tương phản theo bộ kiểm tra hiện tại. Ảnh sau tạm dừng có bảng nổi che một phần tên nên không thể tái dựng chính xác toàn bộ kết quả OCR của các khung trước đó. Mã cũ đọc nửa độ phân giải toàn màn hình và loại cả thẻ khi thiếu tên hoặc số bảng ngọc; vùng tên còn yêu cầu cạnh trái ≥0.325 dù tên có thể bắt đầu ở phần thẻ bên trái. Khi thiếu thẻ, vòng xác minh đầu danh sách vẫn tiếp tục vuốt.

- Adapter Pro dùng OCR riêng vùng x=0.28..0.84, y=0.18..0.90 ở 2× độ phân giải gốc trên cùng khung ảnh. Kết quả dòng và phần tử thay phần OCR thấp trong vùng danh sách; chứng cứ popup/tab và hero ngoài vùng vẫn giữ riêng. Một lần đọc crop thất bại không xóa kết quả toàn màn hình đã có.
- Vùng tên nhận cạnh trái từ 0.29, vẫn giới hạn đúng hàng tiêu đề của nút, chiều rộng, tên không rỗng và không phải dòng chiến lực/tỷ lệ thắng. Số bảng ngọc, đủ hàng thành phần và thẻ không bị cắt vẫn bắt buộc.
- Thẻ chưa đọc đủ sẽ được đọc lại trên danh sách đứng yên tối đa hai lần. Không vuốt hoặc áp dụng trong khoảng này. Nếu vẫn thiếu, báo số nút/số thẻ đủ và lưu ảnh chẩn đoán trong lượt của người dùng, không thêm ảnh vào mã nguồn.
- Không bỏ yêu cầu chứng minh đầu/cuối, chuyển động thẻ, danh sách ngắn hai chiều, catalog thứ hạng, khớp duy nhất trước chạm, xác minh đã áp dụng hay khôi phục đúng top 1. Các ca 5/ít hơn/không có đề cử và không chụp lại top 1 giữ nguyên.
- Kiểm thử tổng hợp bao gồm tên bắt đầu ở x = 0.300/0.310/0.324, OCR số bảng ngọc xung đột, thẻ thiếu/chỉ có nút/thẻ bị cắt; đọc thiếu tạm thời không thêm lần cuộn và không mất thứ hạng; thiếu kéo dài dừng sau đọc lại thay vì 16 lần vuốt. Ảnh người dùng chỉ kiểm tra cục bộ, không đưa vào repo. Chưa xác nhận thu thập trên điện thoại/game thật.


## v0.13.46: số combo tự chọn, đọc nút bằng điểm ảnh, chờ Chuẩn bị đứng yên

- **Luôn chạy hết tướng.** Bước `pro-builds` luôn chạy hết danh sách, kể cả macro cũ có lưu `heroes` nhỏ hơn. Chọn tướng vẫn xác minh Tất cả và bỏ tích Đã nhận mỗi lần mở.
- **Số combo mỗi tướng:** lưu ở `proCombos` của macro và `combos` của bước, từ 1 đến 300 (300 là mọi đề cử). Macro cũ không có khóa này thì lấy 5.
- **Nút Dùng combo đọc bằng điểm ảnh** (`ProPixels.buttons`):
  - Viên thuốc vàng ở x .7025–.8135, chiều cao .058.
  - Một hàng được tính là vàng khi một nửa đoạn x .715–.80 là vàng (r>200, g>150, b<130, r−b>110). Khe do chữ tối cắt ngang được nối lại nếu ≤ .03.
  - Nút chạm mép khung danh sách (.197/.891) bị bỏ qua.
  - Đo trên ảnh: .41/.776 ở đầu danh sách, .468/.837 và .304/.672 khi đã cuộn. Không có nút giả trên Chuẩn bị, hai bảng Chọn tướng và các màn Chiến khu.
- **Các lần nhìn chỉ dùng điểm ảnh** khi về đầu danh sách, sau mỗi lần kéo và khi đọc lại: không OCR. OCR chỉ còn dùng để mở popup và tab, xác minh Chuẩn bị, và xác định danh sách trống.
- **Bước kéo .24** (trước là .17). Vẫn nhỏ hơn .27, ngưỡng mà một bước kéo có thể bị hiểu nhầm thành ngắn hơn một thẻ. Mỗi nút vẫn được thấy ở ít nhất 2 khung.
- **Chuẩn bị đứng yên:** sau Dùng combo và OCR xác nhận Chuẩn bị, app so lưới xám 16×12 của bảng phải (x .59–.95, y .15–.80) giữa các lần nhìn cách nhau 0,5 giây. App chụp khi hai lần liên tiếp giống nhau (sai khác trung bình < 3). Sau 6 lần vẫn đổi thì vẫn chụp và ghi nhật ký. Lần dùng lại đề cử 1 ở cuối không cần chờ.
- **Danh sách trống:** app đọc tối đa 5 lần cách nhau 1,5 giây, hoặc 10 lần nếu game hiện "đang tải", "kết nối", "thử lại". Nếu game ghi rõ là không có đề cử thì kết luận ngay.
- **Kiểm thử:**
  - `ProPixelsTest` chạy trên 7 ảnh của bạn (đã làm mờ tên, ảnh người đề cử và UID). Kiểm tra: vị trí nút, không có nút giả, thứ hạng, khoảng cuộn, bảng Chuẩn bị, Đã nhận đang tích và Tất cả trên cả hai bảng Chọn tướng.
  - `ProSimpleReaderTest` có thêm các ca: số combo, Chuẩn bị vẽ chậm, tab Pro tải chậm.

## v0.13.49: nút Dùng combo theo màu thật của máy; tìm lại khi mất dấu

- Trên ảnh 2400×1080 của máy bạn, nửa trên nút có màu (200, 171, 66), nửa dưới (213, 190, 84). Quy tắc cũ đòi đỏ trên 200, nên nửa trên không được tính, nút bị tách đôi quanh chữ và không nhận ra. Quy tắc mới: đỏ trên 175, xanh lá trên 135, xanh dương dưới 140, đỏ trừ xanh dương trên 90.
- Kiểm thử bằng ảnh lúc mất dấu trong lượt của bạn (`pro-list-device.jpg`, đã che tên, ảnh người chơi và UID) và một nút vẽ đúng màu máy.
- Mất dấu thẻ khi cuộn: về đầu danh sách, đếm lại một lần cho đề cử đó. Lần thứ hai mới bỏ qua, giữ các ảnh đã chụp.

## v0.13.50: Chọn tướng không vuốt thử và kéo về đầu ở mỗi tướng

- Lượt bạn gửi mất 3–4 phút mỗi tướng. Mỗi lần mở Chọn tướng, app vuốt thử 30% màn hình (danh sách trôi xa), rồi kéo lên từng đoạn 24% chậm, mỗi đoạn một lần nhìn, cho tới khi hai đoạn liền không nhích.
- `ProHeroPicker` giờ đi như Chiến khu:
  - Vuốt thử 10% màn hình (`scroll.probe-flick`, .80 → .70). Về đầu bằng lướt nhanh (`scroll.reset-flick`, .40 → .85): hai lần lướt liền mà ảnh tướng không đổi là tới đầu. Hàng đầu vẫn phải đủ ảnh và bắt đầu không thấp hơn 37% chiều cao.
  - `ProHeroPicker.Memory` (một bộ cho cả lượt, trong `ProCollectionSession`) nhớ hình đầu danh sách đã xác minh, chỗ vừa chọn tướng và độ cuộn lúc đó.
  - Lần mở sau: nếu ảnh tướng khớp chỗ cũ (đo bằng `BoardPickerMotion.displacement`) và tướng cần chọn không ở phía trên màn hình, app tìm tiếp từ đó. Nếu khớp hình đầu danh sách thì tìm từ đầu. Chỉ khi cả hai đều không khớp, app mới vuốt thử và về đầu.
  - Nhật ký ghi "Chọn tướng còn ở chỗ lượt trước", "mở ở đầu danh sách như lần trước" hoặc "đã xác minh đầu danh sách".
- Kiểm thử (`ProHeroPickerTest`): 30 tướng liên tiếp chỉ vuốt thử và về đầu ở tướng đầu tiên. Chọn tướng mở ở đầu mỗi lần thì nhận ra đầu đã biết. Danh sách bị dời đi chỗ khác vẫn chọn đúng tướng.

