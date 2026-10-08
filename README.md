# AOV Mobile Collector — v0.1

App thử nghiệm trên Android 11+ để chụp dữ liệu menu Liên Quân VN bằng macro người dùng định nghĩa. Không sử dụng giả lập, không đăng nhập hộ, không đọc mật khẩu, không có quyền Internet. Macro/autoclick trên máy thật vẫn có thể ảnh hưởng tài khoản: đây không phải cam kết Garena cho phép tự động hóa.

## Cài và thử
1. GitHub → Actions → Build APK → artifact `aov-mobile-collector-apk` → giải nén APK và cài.
2. Mở app, đọc thông báo quyền và bật AOV Collector trong Cài đặt Trợ năng.
3. Android có thể yêu cầu cho phép cài app không rõ nguồn gốc hoặc cho phép cài đặt hạn chế trong trang thông tin ứng dụng. Chỉ bật khi tin tưởng bản bạn tự build.
4. Giữ macro `capture-test`, bấm **Lưu macro & hiện bảng nổi**.
5. Đăng nhập game thủ công, mở menu tướng ở màn hình ngang. Bấm **Chạy** trên bảng nổi.
6. App chờ 3 giây rồi chụp một ảnh. Bảng nổi tự ẩn khỏi ảnh.
7. Quay về app → **Xuất toàn bộ ảnh + metadata ZIP** để lưu vào Downloads hoặc vị trí bạn chọn.

Ảnh lưu riêng trên máy. Gỡ app sẽ xóa ảnh chưa xuất. Không dùng trong trận đấu.

## Có trong v0.1
- Tap/swipe/back/wait/screenshot; tọa độ theo tỉ lệ màn hình.
- JSON editor, nhập JSON qua trình chọn file.
- Bảng nổi kéo được: chạy, tạm dừng, chụp riêng, bỏ bước, dừng, ẩn.
- Kiểm tra foreground package và chiều màn hình trước từng bước.
- Checkpoint chỉ tăng sau khi bước hoàn thành; tiếp tục theo hash macro.
- Ảnh PNG và metadata từng lượt, xuất ZIP.
- GitHub Actions build APK + Android lint.

**Chưa có:** Compose UI, ghi thao tác Record, template matching, danh sách tướng/lặp, đồng bộ Wi-Fi. Giao diện bản đầu dùng Android Views thuần để giảm phụ thuộc. Không có macro thu thập mọi tướng vì chưa có tọa độ thực tế trên máy người dùng. Không có APK được xác minh trên điện thoại thật cho đến khi người dùng thử.

## Build
Android SDK 35, JDK 17, Gradle 8.9:
```
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
- Updating v0.1 may require uninstall because its CI signing key was ephemeral. Export and verify the old ZIP first, then install v0.2 and restore the ZIP. The repository contains a public development-only signing key to keep future debug APK signatures stable; do not use it for production releases.
