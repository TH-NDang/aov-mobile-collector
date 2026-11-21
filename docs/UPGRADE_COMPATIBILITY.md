# AOV Collector: cập nhật APK không mất dữ liệu

## Vì sao APK `0.4.7` (versionCode 13) không cài đè được lên `0.13.20` (versionCode 51)?

Bản `codex/pro-builds-collector-v2` được tạo từ `main`, trong khi `main` chậm hơn nhánh phát triển `codex/fix-statistics-warzone-filter`. Vì thế bản đó:

- không chứa icon `@mipmap/ic_launcher` và `@mipmap/ic_launcher_round` đã có ở nhánh phát triển;
- bỏ sót nhiều màn hình, cải tiến và hệ thống thu thập Pro đã phát triển;
- có `versionCode=13` nhỏ hơn `51` nên Android từ chối cập nhật như một phiên bản mới.

Hai nhánh lưu cùng một blob khóa debug `debug-keystore.base64` (SHA Git `10d16da55a42b7d24c5631faf185102778fd0d95`) và dùng cùng `applicationId=vn.ndang.aovcollector`. Do đó không được mặc định quy trách nhiệm cho chữ ký khác khi lỗi thực tế có thể là APK thấp phiên bản.

## Quy tắc từ phiên bản 0.13.21

- Build từ nhánh phát triển đã có Pro Builds, icon, các tiện ích và tiến trình thu thập.
- `versionCode=52` (lớn hơn bản 0.13.20, code 51).
- Giữ nguyên `applicationId`, file khóa ký debug cố định, quyền truy cập ảnh nội bộ, giao diện và icon launcher đã có.
- CI phải kiểm tra icon trong APK (không chỉ có file XML trong repository), mã package, versionCode và chữ ký trước khi phát hành APK.
- Đừng **gỡ ứng dụng** để thử khắc phục lỗi cập nhật trước khi đã xuất và kiểm tra ZIP dữ liệu. Ứng dụng lưu dữ liệu trong bộ nhớ riêng và `allowBackup=false`; gỡ app sẽ xóa dữ liệu đó.
- Nếu người dùng đang dùng APK **ký bởi khóa khác** (nhất là các bản thử nghiệm v0.1/v0.2), việc tăng phiên bản không giải quyết được lỗi cài đè; cần lấy đúng khóa cũ hoặc xuất ZIP trước khi đổi ứng dụng. Không có cách an toàn để buộc Android cài đè khi không có chữ ký tương thích.

## Kiểm tra bản đang cài (ADB, nếu cần)

```shell
adb shell dumpsys package vn.ndang.aovcollector | grep -E 'versionCode|versionName'
adb install -r path/to/app-arm64-v8a-debug.apk
```

Nếu `adb install -r` báo `INSTALL_FAILED_VERSION_DOWNGRADE`: APK mới có versionCode thấp hơn.
Nếu báo `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: khác chữ ký (hoặc xung đột cùng package).
Nếu báo lỗi khác, kiểm tra chính xác mã lỗi thay vì gỡ app.

**Ghi chú:** bản cập nhật tương thích này chỉ sửa nền mã nguồn và đóng gói. Chưa kết luận thuật toán thu thập Pro Builds trên game thật đã hoạt động hoàn chỉnh.
