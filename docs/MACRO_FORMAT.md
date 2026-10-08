# Macro JSON

`name`: chữ, số, `_`, `-`; `targetPackage`: `com.garena.game.kgvn`; `orientation`: `landscape` hoặc `portrait`; `steps`: 1–2000 bước.

| type | Trường | Ý nghĩa |
|---|---|---|
| tap | x, y | Tọa độ 0..1 trên toàn màn hình |
| swipe | x, y, toX, toY, ms | Vuốt; ms mặc định 500, từ 100..5000 |
| wait | ms | Chờ 0..60000 ms |
| back | không | Android Back, không phải nút quay lại Unity |
| screenshot | name | Tên ảnh chỉ chữ/số/_/- |
| pick | index | Chạm thẻ tướng thứ `index` (từ 0, đọc theo hàng) trong danh sách Tất cả tướng. Tự đưa danh sách về đầu ở lần đầu, cuộn từng đoạn và đo độ cuộn bằng ảnh chụp; hết danh sách thì kết thúc lượt |

Ví dụ tọa độ minh họa, cần thay theo máy thật:
```json
{"name":"hero-one","targetPackage":"com.garena.game.kgvn","orientation":"landscape","steps":[{"type":"tap","x":0.8,"y":0.75},{"type":"wait","ms":1200},{"type":"screenshot","name":"skill-1"}]}
```

Đặt tên ảnh `butterfly-skill-1` để phân biệt. V1 lưu theo lượt, chưa tự tạo thư mục từng tướng. Điểm x/y phải hiệu chỉnh trên ảnh toàn màn hình, gồm cả mép hệ thống nếu có. App không tự nhận diện biểu tượng kỹ năng. Khi sửa JSON, checkpoint cũ bị bỏ qua nếu hash thay đổi. **Bỏ bước** chỉ dùng lúc tạm dừng và bước hiện tại đã kết thúc; chỉ bỏ một bước, chưa bỏ cả tướng.
