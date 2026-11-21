# Chiến khu – Mở từng người chơi trong top

**Từ v0.13.46.** Bật sẵn bằng ô **Bấm từng người trong top** trong form Chiến khu (macro: `"boardPlayers": true`, bước `board`: `"players": true`). Macro cũ không có khóa này cũng được bật. Bỏ tích thì chạy như trước: chỉ chụp danh sách.

## Luồng cho mỗi bảng (Tỉnh, Miền, Việt Nam, Toàn máy chủ)

"Số 1 máy chủ" không đổi: vẫn chụp 1 ảnh rồi quay lại.

1. Chụp màn danh sách như trước (`hero-001-quoc-gia-01`).
2. Với mỗi người trên màn đó có hạng ≤ top của bảng (Việt Nam top 3, Toàn máy chủ top 10…), theo thứ tự hạng:
   1. Bấm vào **tên** trên dòng (x .45). Chờ thẻ người chơi hiện bên phải: 3 biểu tượng ở đáy thẻ.
   2. Bấm **biểu tượng người** (biểu tượng đầu, .80/.91). Không bao giờ bấm Kết bạn hoặc Báo cáo.
   3. Chờ **Thông tin kiện tướng** tải xong rồi chụp `…-hang-01-ho-so`.
   4. Bấm **sao chép tên** (.254/.169) và đọc bộ nhớ tạm (từ v0.13.50; trước đó app bấm ảnh đại diện và chụp ảnh lớn).
   5. Bấm **con mắt** (.2775/.169). Khi OCR thấy dãy UID trên dòng UID của hộp (y .30–.40, x .38–.64), chụp `…-hang-01-uid`.
   6. Bấm **sao chép UID**: biểu tượng trắng nhỏ ngay sau dãy số, tìm bằng điểm ảnh (56,6% trên ảnh của bạn); không thấy thì lấy mép phải dãy số + .011. Đọc bộ nhớ tạm.
   7. Ghi tên, UID vào `players.json`, rồi quay lại từng bước có nhìn màn hình: hộp UID có thể nhận lần bấm mũi tên đầu, còn hồ sơ thì bấm mũi tên, còn thẻ thì đóng thẻ.
   8. Kiểm tra danh sách còn đúng chỗ cũ rồi mới bấm người tiếp theo.
3. Kéo xuống màn sau khi còn người trong top chưa mở, rồi lặp lại.

Ảnh nằm trong thư mục tướng như ảnh danh sách.

## Tên và UID (v0.13.50)

- `players.json` ở thư mục lượt, theo khóa người chơi: `hero` (tên tướng đọc trên thẻ), `name`, `uid`, `uidSource` (`copy`: từ nút sao chép, `ocr`: chữ đọc trong hộp), giá trị chưa có là `null`.
- **Đọc bộ nhớ tạm** (`ClipboardPeek.kt`): từ Android 10, chỉ ứng dụng có cửa sổ đang được chọn mới đọc được bộ nhớ tạm. Vì vậy dịch vụ Trợ năng thêm một cửa sổ 1×1, trong suốt, không nhận chạm. Khi cửa sổ được chọn, app đọc bộ nhớ tạm rồi gỡ cửa sổ ngay để game được chọn lại. Sau 1,5 s vẫn chưa được chọn thì app thử đọc một lần rồi gỡ.
- Trước mỗi lần bấm sao chép, app xoá bộ nhớ tạm. Ghi không cần được chọn, nên chữ cũ không bị nhận là tên hay UID mới.
- Bộ nhớ tạm không cho đọc: `name` là `null` (app không đoán tên), UID lấy từ OCR với `"uidSource": "ocr"`, nhật ký ghi "UID đọc từ hộp".
- Con mắt không mở hộp sau khoảng 3 s: bỏ qua UID của người đó và ghi "không hiện hộp UID". App không bấm vào chỗ hộp lẽ ra hiện, vì ở đó là hình tướng của trang.

## Chờ dữ liệu tải

Thông tin kiện tướng lấy qua mạng, có khi chậm. Vì vậy app không chụp ngay sau khi bấm:

| Màn | Nhận ra bằng | Chỉ chụp khi | Chờ tối đa |
| --- | --- | --- | --- |
| Thẻ người chơi | 3 biểu tượng trắng ở đáy thẻ (độ chi tiết 3.8, nền danh sách 0.3) | Thấy thẻ | khoảng 8 s, rồi bỏ qua người đó |
| Thông tin kiện tướng | Tiêu đề OCR, hoặc 2 nhãn "Tỷ lệ thắng", "Lịch sử đấu", "Uy tín", "Hiện tướng" | Bảng trái (ảnh, tên, số liệu) giống hệt ở 2 lần nhìn liên tiếp, số trận/tỷ lệ thắng đã vẽ, ảnh đại diện đã vẽ | khoảng 25 s, rồi chụp kèm ghi chú "hồ sơ tải lâu" |
| Hộp UID (con mắt) | OCR thấy dãy 10–20 chữ số trên dòng UID của hộp | Thấy dãy số | khoảng 3 s, rồi bỏ qua UID |

Dòng chữ tên danh hiệu dưới số liệu chạy ngang liên tục, nên không tính vào phép so "giống hệt". Ảnh đại diện trơn (ảnh mặc định) vẫn được chụp sau 6 lần nhìn mà không đổi.

## Vị trí trong danh sách

- Mỗi dòng được tìm theo số hạng bên trái ảnh (cột x .30–.33). Chỉ bấm dòng có ảnh nằm trọn trong khung, tâm dòng trong khoảng .24–.80.
- Hạng của dòng tính từ khoảng đã cuộn: lúc mới mở tab, hạng 1 ở .284, mỗi dòng cách .1517 (đo lại trên máy khi mở tab).
- Khoảng cuộn đo bằng cách so cột ảnh đại diện giữa hai lần chụp (như lưới Chọn tướng). Không đo được thì căn theo nhịp dòng với lần kéo .36.
- Khi quay lại mà danh sách đã dịch, app đo lại từ chỗ cũ. Nếu dịch quá xa (ví dụ game đưa danh sách về đầu), app bấm lại tab và đo từ đầu. Vẫn không được thì bỏ qua người còn lại của bảng đó, có ghi chú.
- Danh sách ít người hơn top (dòng dưới người cuối để trống) thì dừng, không kéo thêm.

## Lạc đường và tiếp tục

- Mỗi bước quay lại đọc màn hình bằng điểm ảnh, không chắc thì OCR: còn ảnh lớn (lượt cũ) thì bấm X, còn Thông tin kiện tướng hoặc hộp UID thì bấm quay lại, còn thẻ thì đóng thẻ.
- Quá 8 bước mà chưa về danh sách: vào lại Chiến khu từ đầu và chụp lại tướng đó; người đã chụp được bỏ qua, người gây lạc không thử lại. Lạc quá 3 lần trong một bước thì dừng và báo.
- Khi đang ở Thông tin kiện tướng (ví dụ lượt trước bị dừng giữa chừng), đường vào Chiến khu bấm quay lại trước.
- Tạm dừng rồi Tiếp tục: người đã xong (hồ sơ, tên, UID) được bỏ qua (khóa `hero-001-quoc-gia-hang-02`).

## Kiểm thử

- `BoardPlayersTest`: ảnh thật của bạn (tên, ảnh người chơi, khu vực, UID đã làm mờ) `board-players-vn.jpg`, `board-players-tinh.jpg`, `board-player-card.jpg`, `board-player-profile.jpg`, `board-player-photo.jpg`. Kiểm tra: 4 dòng màn đầu đúng chỗ, nhịp dòng, thẻ, danh sách sau thẻ vẫn đúng chỗ, đo khoảng cuộn, hồ sơ đã vẽ, ảnh lớn làm tối trang, nhận trang Thông tin kiện tướng.
- `BoardPlayersTest.findsTheUidCopyIconInTheEyesBox`: ảnh hộp UID của bạn `board-player-uid-box.jpg` (tên, ảnh, giới thiệu, ngày sinh, khách và cả hai UID đã làm mờ); biểu tượng sao chép UID tìm được ở .566.
- `BoardTest`: game giả lập có thẻ, hồ sơ, nút sao chép tên, con mắt, hộp UID nhận lần bấm đầu, bộ nhớ tạm bị Android chặn, con mắt không mở hộp, thời gian tải, danh sách ngắn, danh sách về đầu sau khi quay lại, tiếp tục sau khi dừng.

## Chưa kiểm chứng trên game thật

- Bấm vào tên trên dòng có mở thẻ không (ảnh của bạn chỉ cho thấy thẻ đã mở). Nếu không, dạy lại điểm `board.v1.player.row`.
- Sau khi quay lại từ Thông tin kiện tướng, thẻ còn mở hay không, danh sách giữ chỗ hay về đầu: cả hai trường hợp đều đã xử lý.
- Ảnh khi đang tải trông thế nào: app chỉ dựa vào "không còn thay đổi".
- Cửa sổ 1×1 có được Android cho chọn để đọc bộ nhớ tạm trên máy bạn không, và game có phản ứng gì khi mất chọn trong chốc lát. Nếu `players.json` chỉ có UID dạng `ocr` và không có tên, nghĩa là máy không cho đọc.
- Mũi tên quay lại khi hộp UID đang mở: đóng hộp hay rời trang. Cả hai đều đã xử lý, vì app nhìn lại sau mỗi lần bấm.

## Chọn tướng: về đầu danh sách (v0.13.49)

- Hàng tướng đầu nằm ở 29,4% chiều cao khi danh sách ở đầu (cả khi có và không có Đã nhận). App nhận hàng đầu từ 28,9% tới 37,3%; trước đây chỉ từ 32%, nên lượt của bạn dừng với "Chưa thấy hàng đầu đủ ảnh tại vị trí chuẩn".
- Vuốt thử chiều cuộn là một cú vuốt nhanh ngắn (10% màn hình), không để danh sách trôi xuống đáy.
- Sau khi đã lướt ngược lên được, một lần lướt không làm danh sách nhích là đủ để coi là đầu danh sách.
- Hình đầu danh sách đã xác minh được nhớ lại. Lần mở Chọn tướng sau mà hình giống vậy thì đi thẳng tới tướng cần chọn.

