# Nhập mã sự kiện cho nhiều tài khoản (từ v0.13.47)

Tác vụ trước đây chỉ làm được **Thập kỷ chung sức**. Giờ nó tách thành hai phần:

- **Phần chung**, dùng cho mọi sự kiện, viết một lần trong `EventInviteRunner`.
- **Phần riêng** của từng sự kiện: một "công thức" (`EventCodeRecipe`).

Sự kiện kết thúc thì chỉ bỏ hoặc thêm công thức. Phần chung không phải sửa.

## Phần chung

1. **Từng tài khoản đã lưu:** đăng xuất, đăng nhập Garena. Tài khoản và mật khẩu nằm trong Android Keystore như trước.
2. **Từ sảnh vào Sự kiện:** bấm nút "Sự kiện" ở góc phải trên.
3. **Tìm sự kiện theo tên** trong danh sách bên trái, ví dụ "-Ủy Thác của Shuna-".
   - Chưa thấy thì kéo danh sách xuống, tối đa 4 lần, rồi kéo lên lại.
   - Vẫn không thấy thì dừng, báo kiểm tra tên sự kiện.
4. **Popup quà, hướng dẫn trên đường đi:**
   - Các loại đã biết được xử lý như trước: quà chào mừng, "Chúc mừng… nhận được… chạm để tiếp tục", hướng dẫn lần đầu.
   - Popup lạ có dòng bảo chạm để đóng hoặc tiếp tục ("Chạm vào vùng trống để đóng", "Ấn để tiếp tục"…) được chạm để đóng, tối đa 6 lần mỗi tài khoản. Phần nhận dạng dùng chung với lúc vào game (`GamePopups`).
5. **Sổ tiến độ** (`event-invite-progress.json`):
   - Ghi `submit_pending` **trước** khi bấm nút gửi.
   - Ứng dụng bị tắt giữa chừng thì lần sau chỉ kiểm tra kết quả, **không bao giờ gửi mã lần hai**.
6. **Sau khi gửi:**
   - Popup quà được chụp lại và tính là quà.
   - Popup chạm để đóng được chụp rồi đóng.
   - Sau đó quay về sảnh: X, mũi tên quay lại, đóng popup.

## Phần riêng: công thức

| Công thức | Phần riêng |
| --- | --- |
| **Thập kỷ chung sức** (`Anniversary`) | Giữ nguyên như cũ: Tổng hợp sự kiện → Thập kỷ chung sức → Nhập mã mời → popup Hỗ trợ bạn bè (đọc 0/1) → Xác nhận → đếm Vé nhảy. |
| **Ô nhập mã trên trang sự kiện** (`Field`) | Tên sự kiện trong danh sách, tab chứa ô (có thể trống), chữ gợi ý trong ô (mặc định "Nhập mã"), nút bên ô (mặc định "Dùng"). |

**Ủy Thác của Shuna** là công thức `Field` có sẵn: tab "Nhiệm vụ chia sẻ", ô "Nhập mã để nhận nhiệm vụ do bạn bè chia sẻ", nút "Dùng". Vị trí đo trên ảnh bạn gửi.

Một sự kiện mới có ô nhập mã giống vậy **không cần sửa app**. Trong form chọn "Sự kiện khác" rồi gõ các tên như trong game. Sự kiện có cách nhập khác hẳn (popup riêng, nhiều bước) thì cần viết một công thức mới, phần chung giữ nguyên.

### Công thức `Field` làm gì

1. **Trang sự kiện:**
   - Đã thấy ô và nút: đi tiếp.
   - Chưa thấy: bấm tab, tối đa 3 lần.
   - Chưa mở sự kiện: bấm tên trong danh sách, tối đa 3 lần.
2. **Đếm lượt:** đọc số trên tab ("Nhiệm vụ chia sẻ 0/2"). Chỉ số nằm trên tab hoặc ngay dưới tab mới được tính. Đã đủ (2/2) thì ghi "đã đủ lượt" và không nhập.
3. **Nhập mã:** bấm vào ô, xóa chữ có sẵn trong ô bàn phím rồi gõ mã, bấm OK. Sau đó đọc lại mã trong dải ngang từ ô tới nút: phải đúng mã mới đi tiếp.
4. **Gửi:** ghi `submit_pending` vào sổ rồi bấm nút **một lần**.
5. **Kết quả:**

   | Thấy gì | Ghi nhận | Ảnh |
   | --- | --- | --- |
   | Popup quà | Đã nhận, tính 1 quà | `event-01-code-reward` |
   | Chữ báo lỗi (không hợp lệ, không tồn tại, hết hạn, đã tối đa, không thể, của chính mình…) | Mã bị từ chối | `event-01-code-rejected` |
   | Chữ "thành công", hoặc số trên tab tăng (0/2 → 1/2) | Đã nhận | `event-01-code-done` |

   Không thấy gì rõ sau khoảng 8 lần nhìn: dừng và giữ `submit_pending`, không gửi lại.

Lựa chọn sự kiện được lưu trong macro (`eventRecipe`; bước có `recipe`). Macro chỉ chứa tên hiển thị trong game, **không chứa mã**. Macro cũ không có khóa này vẫn là Thập kỷ chung sức.

## Kiểm thử

- `EventCodeRecipeTest`:
  - Đọc trang Shuna với chữ đặt đúng vị trí trên ảnh của bạn: tên trong danh sách, tab, ô, nút, số lượt, chữ báo lỗi và báo thành công.
  - Game giả lập chạy nhiều tài khoản: tài khoản đã đủ lượt, quà sau khi nhập, mã bị từ chối, sự kiện nằm sâu trong danh sách, popup quà sau đăng nhập, lượt gửi bị gián đoạn không gửi lại.
- Các kiểm thử Thập kỷ chung sức cũ vẫn chạy và vẫn qua.

## Chưa kiểm chứng trên game thật

- Màn hình sau khi bấm **Dùng** ở sự kiện Shuna chưa có ảnh. App nhận "thành công" qua chữ trên màn hình hoặc số trên tab tăng.
- Popup quà trên đường vào sự kiện: chỉ xử lý loại có dòng bảo chạm để đóng hoặc tiếp tục. Popup chỉ có nút X hoặc nút bấm khác thì app chờ rồi dừng an toàn.
- Nhóm sự kiện thu gọn (như "Kho Báu Thời Gian ⌄") chưa được mở ra để tìm bên trong.
