# Thu thập Bách khoa Cờ Liên Quân

Nhánh triển khai: `codex/chess-encyclopedia-collector`, phát triển từ `claude/dark-theme`.

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
- **Kéo và chụp đến cuối**: bật thì chụp nhiều trang chồng mép cho đến khi nội dung không di chuyển; tắt thì chỉ trang đầu của mỗi tab và của mỗi chi tiết.
- **Mở từng quân cờ**: chỉ áp dụng cho tab **Tất cả quân cờ**, mở thẻ quân cờ và chụp chi tiết (có thể kéo).
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
    │   │   └── tuong/
    │   │       └── violet-xxxxxxxx/
    │   │           ├── ...__001.png
    │   │           └── ...__002.png
    │   └── phe-nghe/
    │       └── ...__001.png
    └── trang-bi/
        └── tat-ca-trang-bi/
            ├── ...__trang__001.png
            └── vat-pham/
                └── quyen-truong-rhea-xxxxxxxx/
                    └── ...__001.png
```

Tên item lấy từ OCR ở phần tiêu đề popup khi có thể; hậu tố fingerprint giúp tránh đụng tên khi một tên OCR không được nhận ra. Tất cả ảnh giữ nguyên bản gốc PNG. Xuất ZIP tại Lịch sử giống các macro khác; ZIP chứa các thư mục lồng nhau.

**Tạm dừng / Dừng / Tiếp tục** dùng chung bảng nổi. Những mục và tab đã hoàn tất được lưu vào `chess-progress.json`. Khi tiếp tục, ứng dụng tìm lại Bách khoa, bỏ qua các mục đã hoàn tất. Nếu dừng ở giữa tab, chương trình có thể phải kéo lại từ đầu tab nhưng không chủ động đánh dấu lại item đã hoàn tất.

## Giới hạn và kiểm thử

- Tọa độ đã hiệu chỉnh từ ảnh giao diện 2048 × 921 do người dùng cung cấp. UI update, độ phân giải khác, DPI, popup bất ngờ hoặc màn hình chưa load có thể khiến thao tác sai.
- Khi chụp ảnh, bảng nổi được ẩn và Android giới hạn tốc độ ảnh. Bước dài có thể bị ngắt ngay khi tạm dừng.
- Chụp đến cuối được xác định bằng độ khác nhau của phần nội dung, thử kéo bổ sung khi không đổi; giới hạn an toàn 65 trang/tab, 16 ảnh/chi tiết. Trường hợp chưa đến cuối mà chạm giới hạn phải kiểm tra bằng tay.
- Danh sách có ảnh động hoặc popup có giao diện mới có thể khiến nhận diện thẻ, đóng popup hoặc xác định điểm cuối không đầy đủ; ảnh chụp toàn trang vẫn được lưu.
- Nên thử **Quân cờ → Tất cả quân cờ**, bật chi tiết, **tắt kéo hết** để kiểm tra thẻ và popup trên máy thực; sau đó bật kéo hết.
- Không dùng tài khoản Liên Quân trên trình giả lập nếu việc đăng nhập có nguy cơ khóa tài khoản. GitHub Actions chỉ build/test app Android; **không thể xác nhận thao tác game thật** khi không có thiết bị/game được đăng nhập.

## Kiểm thử CI

Workflow Build APK chạy unit test, lint, build universal/ARM64 và Android emulator UI smoke test. Unit test kiểm tra lựa chọn tab, tên file theo folder và máy trạng thái giả lập. Kiểm thử đầu-cuối trên app Liên Quân thật vẫn cần thao tác thủ công trên điện thoại.
