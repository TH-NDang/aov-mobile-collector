# Thu thập Bách khoa Cờ Liên Quân

Nhánh sửa mới nhất: `codex/chess-verified-title-progress`, phát triển từ `codex/chess-hero-row-capture-fix`.

## Bắt đầu

Trong **Bảng nổi → chọn macro → Cờ Liên Quân · Bách khoa**, chọn riêng từng ô cần thu thập. Lần đầu **không có mục nào được tự chọn**; bạn phải chọn ít nhất một mục, và các lựa chọn được nhớ cho lần sau.

Nếu bật **Tự mở game**, ứng dụng dùng trình vào sảnh sẵn có, đi tiếp qua:
**Chọn chế độ → Chế độ giải trí → chọn thẻ Cờ Liên Quân → Bắt đầu → Trang chủ/Bách khoa**.
Không chạm **Đấu thường** hoặc **Đấu hạng** trong phòng Cờ Liên Quân.

Popup che đường vào, như **Ghi chú cập nhật** sau mỗi bản cập nhật game, được đóng trước khi bấm tiếp. Xem `GamePopups.kt`: dòng chữ bảo chạm và **Ghi chú cập nhật** được xử lý ngay; nút X hoặc nút bấm của popup lạ chỉ dùng sau khi 2 lần thao tác trên sảnh không có tác dụng.

Nếu không tự mở game, hãy mở sẵn sảnh Cờ Liên Quân hoặc Bách khoa rồi mới nhấn **Chạy**.

## Chụp nhanh (mặc định từ v0.13.45)

Khi đã vào Bách khoa, **Chụp nhanh** (`ChessQuickReader.kt`) chỉ đọc điểm ảnh (`ChessPixels.kt`), không OCR, và đặt tên theo thứ tự trên trang:

| Tab | Tên mục | Ví dụ ảnh |
| --- | --- | --- |
| Tất cả quân cờ | `quan-co-001`… | `quan-co/tat-ca-quan-co/tuong/quan-co-001/quan-co-001-detail-1.png` |
| Tương tác → Phe / Nghề | `phe-001`…, `nghe-001`… | `tuong-tac/phe/phe-001-page-1.png` |
| Trang bị | `vat-pham-001`… | `trang-bi/tat-ca-trang-bi/vat-pham-001-page-1.png` |
| Ảnh gốc Chúc phúc | `chuc-phuc-001`… | theo nhóm ảnh gốc |

Ảnh trang của mỗi tab giữ tên như trước (`<tab>-page-N.png`); trên lưới quân cờ, ảnh trang là các ảnh danh sách chụp sau mỗi lần cuộn. Ảnh gốc quân cờ là `quan-co-001-head`, ảnh chiêu là `quan-co-001-skill-1`.

Cách đọc màn hình bằng điểm ảnh, đo trên ảnh 2000×900 của người dùng:

- **Trang hoặc bảng chi tiết đã hết:** dải đáy (86–99,5% chiều cao) là nền trơn. Khi đó app không kéo thêm.
  - **Trừ Tất cả quân cờ** (từ v0.13.48). Thẻ từ 3 vàng trở lên màu xanh hoặc tím, gần như cùng màu nền, và trơn ở phần dưới tên. Dải đáy rơi vào phần đó thì trơn dù danh sách còn tiếp. Danh sách quân cờ chỉ hết khi kéo mà trang không đổi.
- **Trang đã vẽ xong** (từ v0.13.48):
  - Trang đầu của mỗi tab được chụp khi hai lần nhìn liên tiếp giống nhau.
  - Trang bị: không còn ô đang tải (ô vuông sáng có hình thoi xám). App chờ tối đa 8 lần nhìn; quá thì chụp như đang thấy và ghi nhật ký.
- **Lưới Tất cả quân cờ:** khe giữa 5 cột thẻ là nền trơn từ trên xuống dưới. `ListNavigator` vẫn đo độ cuộn như trước, chỉ không còn cần OCR.
  - Sau mỗi lần kéo, app nhìn lại tới khi hai ảnh liền nhau giống nhau rồi mới đo khoảng cuộn (từ v0.13.49).
  - Khoảng cuộn chỉ được tìm trong tầm lần kéo có giữ ngón có thể đi: quãng ngón tay cộng 8% màn hình (từ v0.13.50). Trên máy bạn, một lần kéo 413 px từng bị đo thành 650 px. Đó là mép vùng so ảnh, nơi hai ảnh chỉ còn trùng một dải hẹp nên khớp nhầm. Kết quả là ô 36 bị tìm sai chỗ và cả danh sách phải đếm lại từ đầu.
  - Chỗ cần bấm mà không thấy thẻ: app kéo lên một đoạn để kiểm tra. Danh sách còn đi gần trọn quãng kéo và phần dưới màn hình còn thẻ thì đếm lại từ đầu danh sách, tối đa 2 lần; ngược lại mới là hết danh sách.
- **Bảng chi tiết đã vẽ xong:** hình tròn góc trên của bảng, và với quân cờ là hình chiêu, đã có chi tiết. Nếu chưa, app nhìn lại tối đa 5 lần.
- **Hộp Save Photo:** thanh tiêu đề đen, ngay dưới là hai nút sáng. Chỉ bấm **Sure** khi thấy đúng hộp này. Màn hình đổi mà không khớp hộp thì đọc OCR, và chỉ bấm Sure khi đọc ra đủ Save Photo, Sure, CANCEL.
- **Thẻ Chúc phúc:** hình nét trắng ở hai cột. Chỉ nhấn giữ hình đủ cao (từ 9% chiều cao) và không chạm mép dưới.
  - **Thẻ đã có ở trang trước** (từ v0.13.50) được nhận theo chỗ: app đo mỗi lần kéo bằng `ChessPixels.blessingProfile` (5 dải dọc danh sách, từ dưới thanh tab tới đáy). Một lần kéo đi khoảng 59% màn hình, ít hơn ở cuối danh sách; hai trang chỉ còn trùng chừng 1/9, nên phép so chấp nhận phần trùng nhỏ cỡ đó. Thẻ ở trang trước, dời lên đúng khoảng đã đo, trùng chỗ (lệch dưới 7% chiều cao) với thẻ trang này thì không bị nhấn lại.
  - Trước đây app so theo hình. Thẻ bị thanh tab che một phần hình nên không khớp, và bị lưu lại (Thường số 11, 12, 25 trong lượt của bạn). Còn ba Chúc Phúc Truyền thuyết dùng chung một hình thì có thể bị coi là một.
  - Không đo được lần kéo: app so theo hình như cũ và ghi nhật ký "không đo được độ cuộn trang N".

Tiến độ được lưu trong `chess-progress.json` với tiền tố `quick:`: tab xong, mục đã mở, ảnh gốc đã lưu. Tab đang dở được chụp lại từ đầu, ghi đè ảnh trang cũ cùng tên.

Giới hạn của chụp nhanh:

- Không có tên quân cờ, trang bị, phe hay nghề trong tên file. Muốn có tên, bỏ chọn **Chụp nhanh** để dùng cách cũ ở các phần dưới.
- Chỉ nhấn giữ một hình chiêu, ở trang đầu bảng chi tiết của quân cờ.
- Dừng giữa một tab Chúc phúc rồi tiếp tục: số thứ tự ảnh gốc có thể lệch nếu lần kéo khác lần trước.

## Những gì có thể tick

| Nhóm bên trái | Tab/mục con |
| --- | --- |
| Thông tin cơ bản | Quy trình cơ bản; Thắng và thua vòng đấu; Thắng và thua trận |
| Quân cờ | Tất cả quân cờ; Phe & Nghề |
| Tương tác | Phe; Nghề |
| Trang bị | Tất cả trang bị |
| Nhiệm vụ | Danh sách nhiệm vụ |
| Chúc Phúc | Thường; Huyền thoại / Sử thi; Truyền thuyết |

Các tùy chọn **độc lập**:
- **Luôn kéo và chụp đến cuối**: mỗi tab hoặc chi tiết được chọn sẽ tự động cuộn và lưu từng trang cho đến khi nội dung không di chuyển. Không có tùy chọn bật/tắt việc này.
- **Mở từng quân cờ**: chỉ áp dụng cho tab **Tất cả quân cờ**, mở thẻ quân cờ và chụp chi tiết (có thể kéo).
- **Ảnh mỗi hàng quân cờ (luôn bật)**: ngoài ảnh toàn trang, lưu thêm một ảnh PNG *cắt riêng phần hàng có chân dung/tên/thuộc tính* trong thư mục \`quan-co/tat-ca-quan-co/hang/\`, trước khi mở bất kỳ tướng nào trên hàng; kể cả khi không chọn **Mở từng quân cờ**.
- **Thứ tự duyệt**: xác định tên và hàng từ ảnh chụp hiện tại; mở theo *trái → phải*, hết hàng mới xuống dưới. Sau khi kéo trang mới, không click lại ảnh hoặc chữ còn sót của hàng trước.
- **Mở từng phe và nghề**: chỉ áp dụng cho **Tương tác → Phe/Nghề**.
- **Mở từng trang bị**: chỉ áp dụng cho **Trang bị**.
- Tự mở game, xác minh UID, và lưu ảnh lịch sử đăng nhập có thể bật/tắt riêng.

## Cấu trúc dữ liệu

Ví dụ, một lượt chụp các tab Quân cờ và Trang bị có cấu trúc:

```text
captures/
└── chess--20261016-.../
    ├── run.json
    ├── macro.json
    ├── chess-progress.json
    ├── events.txt
    ├── quan-co/
    │   ├── tat-ca-quan-co/
    │   │   ├── 0003-chess__quan-co__tat-ca-quan-co__trang__001.png
    │   │   ├── 0003-chess__quan-co__tat-ca-quan-co__trang__002.png
    │   │   ├── hang/
    │   │   │   ├── 0003-chess__quan-co__tat-ca-quan-co__hang__001-violet-xxxxxxxxxx.png
    │   │   │   └── 0003-chess__quan-co__tat-ca-quan-co__hang__002-fennik-xxxxxxxxxx.png
    │   │   └── tuong/
    │   │       ├── violet/
    │   │       │   ├── ...__001.png
    │   │       │   └── ...__002.png
    │   │       └── fennik/
    │   │           └── ...__001.png
    │   └── phe-nghe/
    │       └── ...__001.png
    └── trang-bi/
        └── tat-ca-trang-bi/
            ├── ...__trang__001.png
            └── vat-pham/
                └── quyen-truong-rhea-xxxxxxxx/
                    └── ...__001.png
```

**Đối với quân cờ:** tên thư mục là **tên tướng** chuẩn hoá thành slug (ví dụ \`violet\`, \`fennik\`, \`darcy\`), không phải tên kỹ năng, không thêm mã fingerprint sau tên. Không dùng nhãn nghề bị OCR thêm ký tự biểu tượng, ví dụ **A Đấu sĩ**, làm tên tướng. Nếu thiếu tên thẻ, dùng vị trí nhãn nghề để mở chân dung, rồi xác minh tên từ popup. Nếu tên thẻ khác tên popup, đọc riêng vùng tiêu đề: hai lần liên tiếp cùng tên sẽ xác nhận tên popup và sửa nhãn thẻ, ví dụ **A Đấu sĩ → Arduin**, rồi tiếp tục. Chỉ tạm dừng khi chưa xác minh được tên sau ba lần đọc; không lấy tên chiêu làm tên tướng. Tên đã xác minh dùng cho cả thư mục và dấu hoàn tất để không lưu trùng khi tên thẻ bị OCR khác ở trang sau. Mỗi hàng có một ảnh PNG riêng trong \`hang/\`, ngoài ảnh toàn trang. Các mục khác giữ quy tắc đặt tên cũ. Xuất ZIP tại Lịch sử giống các macro khác; ZIP chứa các thư mục lồng nhau.

**Tiến trình:** trong khi dò danh sách, chỉ hiện **đã lưu N tướng · tổng chưa xác định** và tên đang mở. Số đã lưu tính theo quân cờ đã hoàn tất, kể cả các quân cờ đã lưu trước khi tạm dừng. Thanh chạy thể hiện đang xử lý; bảng nổi và nút thu gọn không hiện phần trăm từ số bước macro hay coi số ô trên trang hiện tại là tổng tướng.

**Từ v0.8.2:** các dòng phe bị cắt ngắn như **4 Giai Điệu H.**, **9 Giai Điệu H..** được loại khỏi danh sách tướng; chữ phe màu xanh không được coi là tên màu trắng chỉ vì có biểu tượng trắng bên cạnh. Sau 5 quân cờ hàng đầu, tác vụ kéo khoảng một hàng thẻ (60% chiều cao màn hình) để đưa tên hàng 2 vào vùng nhìn rõ. Nhật ký ghi rõ số ô nhận diện và mỗi lần kéo danh sách. Giá vàng bị OCR ghép vào tên (**Arduin1**, **Violet O1**, **Violet 01**) được tách bỏ; khi tiếp tục lượt cũ, dấu hoàn tất **violet-o1** được tính cùng **violet**, không cộng thành hai tướng. Ảnh cũ vẫn được giữ.

**Chạy nhanh** mặc định bật trong tùy chọn Cờ Liên Quân, dùng cùng thời gian chờ sau chạm **600 ms** và chuyển tab **450 ms** như chế độ Chạy nhanh của Lấy thông tin tướng; thao tác kéo và giữ cuối kéo cũng ngắn hơn. Có thể bỏ chọn để dùng nhịp chậm hơn. Macro cũ chưa có trường `fast` dùng nhịp nhanh khi chạy bản này. Giới hạn chụp màn hình của Android vẫn được giữ. Nút **Tạo lượt mới** dùng biểu tượng khởi động lại nét liền, giữ nguyên thao tác tạo lượt mới.

**Từ v0.9.0:** lưu nhiều tài khoản tại **Tài khoản (N)** trong app. Bật **Kiểm tra UID · chọn tài khoản khi bấm Chạy** để tự bật Tự mở game và chọn tài khoản cho từng lượt mới. Nút **Quản lý tài khoản** trong form dẫn đến danh sách Thêm/Sửa/Xóa; không khóa ô kiểm tra chỉ vì chưa có UID. Lượt đang dở giữ tài khoản đã chọn, kể cả khi đổi tài khoản mặc định. Tài khoản cũ được chuyển sang danh sách mới; nếu không đọc được mật khẩu thì vẫn giữ UID để nhập lại. Lượt cũ chưa gắn tài khoản và đã qua bước vào game cần tạo lượt mới để xác minh đúng UID; ảnh cũ được giữ.

**Tạm dừng / Dừng / Tiếp tục** dùng chung bảng nổi. Những mục và tab đã hoàn tất được lưu vào `chess-progress.json`. Khi tiếp tục, ứng dụng tìm lại Bách khoa, bỏ qua các mục đã hoàn tất. Quân cờ được đánh dấu bằng tên popup đã xác minh (không bằng fingerprint pixel dễ đổi sau cuộn); hàng được đánh dấu bằng tập tên trong hàng. Nếu dừng ở giữa tab, chương trình có thể phải kéo lại từ đầu tab nhưng không chủ động đánh dấu lại item đã hoàn tất.

## Giới hạn và kiểm thử

- Tọa độ đã hiệu chỉnh từ ảnh giao diện 2048 × 921 do người dùng cung cấp. UI update, độ phân giải khác, DPI, popup bất ngờ hoặc màn hình chưa load có thể khiến thao tác sai.
- Khi chụp ảnh, bảng nổi được ẩn và Android giới hạn tốc độ ảnh. Bước dài có thể bị ngắt ngay khi tạm dừng.
- Chụp đến cuối được xác định bằng độ khác nhau của phần nội dung, thử kéo bổ sung khi không đổi; giới hạn an toàn 65 trang/tab, 16 ảnh/chi tiết. Trường hợp chưa đến cuối mà chạm giới hạn phải kiểm tra bằng tay.
- Danh sách có ảnh động hoặc popup có giao diện mới có thể khiến nhận diện thẻ, đóng popup hoặc xác định điểm cuối không đầy đủ; ảnh chụp toàn trang vẫn được lưu. Thuật toán xác nhận phần chữ tên màu trắng trên thẻ và chân dung tương ứng; nếu OCR không thấy tên, hàng đó cần kiểm tra từ ảnh toàn trang. Không dùng tên chiêu làm tên thư mục.
- Nên thử **Quân cờ → Tất cả quân cờ** và tắt mở chi tiết trước để kiểm tra khả năng cuộn/chụp trên điện thoại; sau đó mới bật thêm chi tiết quân cờ.
- Không dùng tài khoản Liên Quân trên trình giả lập nếu việc đăng nhập có nguy cơ khóa tài khoản. GitHub Actions chỉ build/test app Android; **không thể xác nhận thao tác game thật** khi không có thiết bị/game được đăng nhập.

## Kiểm thử CI

Workflow Build APK chạy unit test, lint, build universal/ARM64 và Android emulator UI smoke test. Unit test kiểm tra lựa chọn tab, tên file theo folder, thứ tự trái-phải từng hàng, nhận diện riêng hàng 2 sau cuộn, tránh trùng tên, ảnh từng hàng và máy trạng thái giả lập. Kiểm thử đầu-cuối trên app Liên Quân thật vẫn cần thao tác thủ công trên điện thoại.


## Ảnh gốc của game từ Pictures (tùy chọn)

Mục này **khác** ảnh chụp màn hình. Game có hộp **Save Photo** với hai nút **Sure** và **CANCEL** khi nhấn giữ hình phù hợp. Tác vụ ảnh gốc chỉ nhấn Sure sau khi nhận ra đầy đủ hộp thoại này; nếu màn hình không khớp, sẽ bỏ qua và ghi nhật ký thay vì chạm bừa.

1. Trong **AOV Collector → Nhập file → Ảnh từ Pictures vào app**, chọn đúng **Bộ nhớ trong → Pictures** (`/storage/emulated/0/Pictures`, theo ảnh thực tế; game lưu tên như `caYyujwz.png (1).jpg`) để cấp quyền đọc một lần bằng bộ chọn thư mục Android.
2. Chọn macro **Cờ Liên Quân · Bách khoa**. Ở mục **Tải ảnh gốc từ game**, tick riêng các nhóm muốn tải: quân cờ, chiêu quân cờ, phe, nghề, trang bị và 3 loại Chúc Phúc. Đây là **chọn phạm vi**, không phải phân loại từng ảnh: app tự gán nhóm theo tab đang thu thập.
3. Khi bắt đầu bước Bách khoa, app **đếm và ghi dấu toàn bộ mã tài liệu hiện có** trong thư mục được cấp quyền, lưu vào \`captures/<run-id>/pictures-session.json\`.
4. Khi game xác nhận Save Photo, app ghi thời điểm và nhóm tương ứng. Ứng dụng tự quét lại Pictures ngay sau Sure trên luồng nền riêng và nhận diện ảnh **mới xuất hiện kể từ mốc đầu lượt**.
5. Trong app, bấm **Ảnh từ Pictures** → **Mở ảnh gốc đã nhập trong Lịch sử** để xem ảnh theo nhóm. Thư viện tự cập nhật khi đang mở. App sao chép các ảnh mới tương ứng vào \`captures/anh-goc-co-lien-quan/<nhóm>/<lượt>/\`. Ảnh ngoài thời gian lưu hoặc không khớp nhóm không được tự gán. Không xóa/sửa ảnh trong Pictures. Ảnh chưa xuất hiện được tự thử lại sau 30 giây khi tiến trình app còn hoạt động; khi mở lại app/dịch vụ cũng tiếp tục từ mốc đã lưu. Có thể chọn **Đồng bộ ảnh mới** để quét lại thủ công.

Kết quả cho biết **trước lượt bao nhiêu ảnh, sau lượt bao nhiêu, số ảnh mới, số ảnh đã sao chép, số ảnh trùng và số ảnh chưa xác định**. File được lưu dưới tên có mã hash để tránh trùng, và sổ theo dõi giúp không nhập lại khi bạn đồng bộ nhiều lần.

**Giới hạn và an toàn:** Nếu bạn đồng thời tạo ảnh cá nhân trong vài giây quanh một lần Save Photo của game, chỉ dùng thời điểm và số lượng không thể xác minh tuyệt đối ảnh nào thuộc game. Vì vậy nên tránh lưu ảnh khác trong lúc macro chạy, hoặc chọn thư mục con chỉ chứa ảnh game. File thiếu thời gian sửa và thuộc nhiều nhóm sẽ không được tự phân loại. Khi bố cục icon thay đổi, thao tác nhấn giữ có thể không mở hộp Save Photo; khi đó app lưu screenshot bình thường và nhật ký để hiệu chỉnh, không giả vờ ảnh gốc đã được lưu.

Thư viện Lịch sử, tìm kiếm và xuất ZIP hỗ trợ PNG, JPEG và WebP đã sao chép. GitHub Actions kiểm thử nhận diện nhóm, đối chiếu trước/sau và nhận diện hộp xác nhận bằng các kịch bản giả lập; thao tác tải thực tế cần kiểm thử trên thiết bị đã đăng nhập Liên Quân.

