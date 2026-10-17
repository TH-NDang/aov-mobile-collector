# Thu thập Bách khoa Cờ Liên Quân

Nhánh triển khai: `codex/chess-hero-row-capture-fix`, phát triển từ `codex/chess-encyclopedia-collector`.

## Bắt đầu

Trong **Bảng nổi → chọn macro → Cờ Liên Quân · Bách khoa**, chọn riêng từng ô cần thu thập. Lần đầu **không có mục nào được tự chọn**; bạn phải chọn ít nhất một mục, và các lựa chọn được nhớ cho lần sau.

Nếu bật **Tự mở game**, ứng dụng dùng trình vào sảnh sẵn có, đi tiếp qua:
**Chọn chế độ → Chế độ giải trí → chọn thẻ Cờ Liên Quân → Bắt đầu → Trang chủ/Bách khoa**.
Không chạm **Đấu thường** hoặc **Đấu hạng** trong phòng Cờ Liên Quân.

Nếu không tự mở game, hãy mở sẵn sảnh Cờ Liên Quân hoặc Bách khoa rồi mới nhấn **Chạy**.

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

**Đối với quân cờ:** tên thư mục là **tên tướng** chuẩn hoá thành slug (ví dụ \`violet\`, \`fennik\`, \`darcy\`), không phải tên kỹ năng, không thêm mã fingerprint sau tên. Ưu tiên tên ở phần đầu bảng chi tiết, dự phòng bằng tên đọc ngay trên thẻ quân cờ. Nếu tên đọc từ thẻ khác tên bảng chi tiết, tác vụ dừng để tránh gán ảnh sai; không đoán tên từ mô tả/chiêu. Mỗi hàng có một ảnh PNG riêng trong \`hang/\`, ngoài ảnh toàn trang. Các mục khác giữ quy tắc đặt tên cũ. Xuất ZIP tại Lịch sử giống các macro khác; ZIP chứa các thư mục lồng nhau.

**Tạm dừng / Dừng / Tiếp tục** dùng chung bảng nổi. Những mục và tab đã hoàn tất được lưu vào `chess-progress.json`. Khi tiếp tục, ứng dụng tìm lại Bách khoa, bỏ qua các mục đã hoàn tất. Quân cờ được đánh dấu bằng tên thẻ (không bằng fingerprint pixel dễ đổi sau cuộn); hàng được đánh dấu bằng tập tên trong hàng. Nếu dừng ở giữa tab, chương trình có thể phải kéo lại từ đầu tab nhưng không chủ động đánh dấu lại item đã hoàn tất.

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

1. Trong **AOV Collector → Nhập file → Ảnh từ Pictures vào app**, chọn thư mục Pictures (hoặc thư mục con ảnh game) để cấp quyền đọc một lần bằng bộ chọn thư mục Android.
2. Chọn macro **Cờ Liên Quân · Bách khoa**. Ở mục **Tải ảnh gốc từ game**, tick riêng các nhóm muốn tải: quân cờ, chiêu quân cờ, phe, nghề, trang bị và 3 loại Chúc Phúc. Đây là **chọn phạm vi**, không phải phân loại từng ảnh: app tự gán nhóm theo tab đang thu thập.
3. Khi bắt đầu bước Bách khoa, app **đếm và ghi dấu toàn bộ mã tài liệu hiện có** trong thư mục được cấp quyền, lưu vào \`captures/<run-id>/pictures-session.json\`.
4. Khi game xác nhận Save Photo, app ghi thời điểm và nhóm tương ứng. Sau khi bạn bấm **Về app**, ứng dụng tự quét lại Pictures và nhận diện ảnh **mới xuất hiện kể từ mốc đầu lượt**.
5. App sao chép các ảnh mới tương ứng vào \`captures/anh-goc-co-lien-quan/<nhóm>/<lượt>/\`. Ảnh ngoài thời gian lưu hoặc không khớp nhóm không được tự gán. Không xóa/sửa ảnh trong Pictures. Có thể chọn **Đồng bộ ảnh mới** để quét lại sau đó.

Kết quả cho biết **trước lượt bao nhiêu ảnh, sau lượt bao nhiêu, số ảnh mới, số ảnh đã sao chép, số ảnh trùng và số ảnh chưa xác định**. File được lưu dưới tên có mã hash để tránh trùng, và sổ theo dõi giúp không nhập lại khi bạn đồng bộ nhiều lần.

**Giới hạn và an toàn:** Nếu bạn đồng thời tạo ảnh cá nhân trong vài giây quanh một lần Save Photo của game, chỉ dùng thời điểm và số lượng không thể xác minh tuyệt đối ảnh nào thuộc game. Vì vậy nên tránh lưu ảnh khác trong lúc macro chạy, hoặc chọn thư mục con chỉ chứa ảnh game. File thiếu thời gian sửa và thuộc nhiều nhóm sẽ không được tự phân loại. Khi bố cục icon thay đổi, thao tác nhấn giữ có thể không mở hộp Save Photo; khi đó app lưu screenshot bình thường và nhật ký để hiệu chỉnh, không giả vờ ảnh gốc đã được lưu.

Thư viện Lịch sử, tìm kiếm và xuất ZIP hỗ trợ PNG, JPEG và WebP đã sao chép. GitHub Actions kiểm thử nhận diện nhóm, đối chiếu trước/sau và nhận diện hộp xác nhận bằng các kịch bản giả lập; thao tác tải thực tế cần kiểm thử trên thiết bị đã đăng nhập Liên Quân.
