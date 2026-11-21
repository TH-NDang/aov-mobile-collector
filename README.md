# AOV Mobile Collector — v0.13.62

## v0.13.62: Thống kê kéo và chụp nhanh hơn, chỉ chụp khi bảng đã vẽ xong, không sót hàng

Phần chụp Thống kê chạy tốt, nhưng mỗi trang mất hơn 3 giây và đôi khi lần kéo có thể bỏ sót.

- **Kéo nhanh hơn:** ngón tay kéo 0,4 giây rồi giữ 0,25 giây (trước là 0,7 + 0,4 giây). Vẫn giữ trước khi nhả nên bảng dừng ngay dưới ngón tay, không trượt tiếp.
- **Chỉ chụp khi bảng đứng yên:** trước đây sau mỗi lần kéo app chờ cứng 1,2 giây rồi chụp, bảng ra sao cũng lấy. Giờ app nhìn liên tiếp và chỉ chụp khi hai lần nhìn giống hệt nhau từng ô: trang nào vẽ nhanh thì chụp sớm, trang nào còn tải hàng, ảnh tướng hay thanh tỷ lệ thì chờ tiếp. Sau 6 lần nhìn vẫn chưa yên thì chụp như trước và ghi "Bảng chưa đứng yên…" vào nhật ký.
- **Lúc mới mở bảng hay đổi bảng, đổi chế độ:** vẫn chờ 6 giây như trước, và giờ phải giống hệt nhau từng ô mới bắt đầu chụp. Trước đây chỉ so trung bình cả bảng, nên một ảnh tướng chưa tải ở góc không bị phát hiện.
- **Đo độ dịch sau mỗi lần kéo:** app tìm lại các hàng của ảnh trước trong ảnh mới. Tìm thấy thì chắc chắn không sót tướng nào. Không tìm thấy thì có thể đã kéo quá: app kéo ngược lại 0,2 màn hình (tối đa 2 lần) rồi mới chụp; vẫn không khớp thì chụp và ghi "có thể sót hàng giữa hai trang". Cuối mỗi bảng nhật ký ghi mỗi lần kéo đi bao nhiêu màn hình.
- **Nhìn liên tiếp nhanh hơn:** các lần nhìn không đọc chữ của Thống kê cách nhau 0,4 giây trên Android 12 trở lên (trước là 1 giây cho mọi máy). Máy nào từ chối thì app tự quay về nhịp 1 giây. Các chế độ khác giữ nhịp cũ.
- Phần nhận ra cuối bảng (kéo 3 lần không đổi) giữ nguyên thời gian chờ, để trang đang tải thêm hàng không bị coi là hết.

Ước tính mỗi trang còn khoảng 2,5 giây thay vì khoảng 3,3 giây trên Android 12 trở lên; chưa đo trên máy thật.

## v0.13.61: Đề cử Pro quay lại chụp tướng còn thiếu ảnh

Theo tướng 89 (Florentino) của lượt Đề cử Pro: chỉ có 2 ảnh trên 3, nhật ký ghi "bỏ qua từ đề cử 3 (không đưa được danh sách về đầu)".

- **Vì sao thiếu:** trước mỗi đề cử, app vuốt danh sách đề cử về đầu và chờ các nút "Dùng combo" đứng yên đúng chỗ. Sau 6 lần vuốt mà chưa yên, app bỏ luôn các đề cử còn lại của tướng đó và coi tướng đã xong. Lúc ấy không có ảnh nào được lưu nên không biết chắc màn hình ra sao; quanh thời điểm đó có nhiều lần game phản hồi chậm (bấm Dùng combo chưa về Chuẩn bị), nên nhiều khả năng danh sách chưa kịp đứng yên.
- **Chờ rồi thử lại:** chưa yên sau 6 lần vuốt thì app chờ 2,5 giây, vuốt lại 6 lần nữa, mỗi lần chờ 1,6 giây thay vì 0,7 giây. Vẫn không được mới bỏ qua, và lần này có lưu ảnh chẩn đoán `…-diagnostic-pro-top.png`.
- **Quay lại tướng thiếu ảnh:** hết danh sách tướng, app quay lại từng tướng có ít ảnh hơn số combo đã chọn (mỗi tướng một lần), chụp tiếp từ đề cử còn thiếu và giữ các ảnh đã có. Tướng 89 của lượt đang dở cũng được quay lại như vậy. Nhật ký ghi "Pro: hết danh sách tướng; quay lại tướng 89 (pro-089…) mới có 2/3 ảnh". Tướng có danh sách đề cử trống thì không quay lại; tướng thật sự chỉ có ít đề cử thì app xem lại một lần rồi thôi.

## v0.13.60: Đề cử Pro không còn dừng vì "Mất lưới khi cuộn tìm tướng"

Theo lượt Đề cử Pro mở lại bằng v0.13.59: chạy tiếp từ tướng 58 tới 103, rồi dừng hai lần liền ở tướng 104.

- **"Mất lưới khi cuộn tìm tướng" ở tướng 104:** ảnh app lưu lúc dừng cho thấy khung Chọn tướng nguyên vẹn; các phép đo trên ảnh (gạch dưới Tất cả, ô Đã nhận, các hàng tướng) đều đạt. Còn lại chỉ có chữ, nên nhiều khả năng đây là chỗ hỏng (chưa xem được trên máy): bộ đọc chữ trả về từng dòng, và sau lần cuộn đầu nó đọc trượt một trong ba chữ Chọn tướng / Tất cả / Đã nhận, sáu lần liền. Giờ, khi khung đã được đọc đủ chữ một lần, lần đọc sau thiếu chữ vẫn được nhận nếu dải tiêu đề đúng là hình đã đọc (hai ảnh cách nhau 17 giờ trên máy giống nhau từng điểm ảnh; tiêu đề danh sách Đề cử Pro trong cùng khung chỉ giống 0,45). Gạch dưới Tất cả và ô Đã nhận vẫn được kiểm tra trên ảnh mới, nên đổi thẻ hay bật Đã nhận vẫn bị phát hiện.
- **Nhật ký ghi rõ hơn:** mỗi lần không nhận được lưới, nhật ký ghi thiếu gì và bộ đọc chữ đã đọc được những dòng nào ở đầu khung, ví dụ "Pro: không đọc được Tất cả (chữ đọc được: …); khung Chọn tướng vẫn đúng hình đã đọc trước nên nhận theo hình".
- **"Đường chạm quá gần cửa sổ khác" lúc 22:18:** một cửa sổ của máy hiện ở giữa cạnh dưới màn hình đúng lúc app sắp cuộn; app dừng ngay và lượt đứng suốt đêm. Giờ app chờ cửa sổ đó ẩn, tối đa 30 giây, rồi mới chạm; quá 30 giây mới dừng. Chưa có thao tác nào được gửi trong lúc chờ.

## v0.13.59: Lỡ bấm Dừng thì vẫn chạy tiếp được lượt cũ

Dừng kết thúc lượt ngay, không hỏi lại. Sau đó nút chính chỉ còn "Tạo lượt mới", làm lại từ tướng đầu, dù thư mục lượt vẫn giữ đủ macro, tiến độ và ảnh.

- **Ngay sau khi bấm Dừng:** bấm nút chính "Tạo lượt mới" thì app hỏi: **Chạy tiếp lượt này** (từ chỗ đã dừng, giữ ảnh và tiến độ) hay **Tạo lượt mới** (làm lại từ đầu).
- **Đã lỡ tạo lượt mới hoặc chọn tác vụ khác:** trong app, mở danh sách lượt, bấm ⋮ ở lượt cũ (nhãn "Đã kết thúc") → **Chạy tiếp lượt này**, rồi mở game và bấm Tiếp tục. Lượt đang mở dở (nếu có) được kết thúc như khi chọn tác vụ khác; ảnh của nó vẫn giữ.
- Chỉ lượt còn bước chưa làm, và có macro.json đúng với macro lượt đã chạy, mới mở lại được. Lượt cần tài khoản sẽ hỏi chọn tài khoản lại nếu tài khoản đã không còn ghi trong lượt.

## v0.13.58: Đề cử Pro không còn dừng ở Chuẩn bị của tướng to như Max

Theo lượt Đề cử Pro dừng ở tướng 58 (Max) và Tiếp tục không đi tiếp được.

- **Chuẩn bị của Max không được nhận ra:** app nhận màn Chuẩn bị khi đọc được đủ "Chuẩn bị", "Trang bị", "Phù hiệu" và "Phụ trợ". Cỗ máy của Max đứng sau bảng bên phải và chuyển động, che mất "Phù hiệu"/"Phụ trợ", nên app tưởng chưa về Chuẩn bị và dừng: "Chọn tướng chưa về Chuẩn bị". Giờ chỉ cần tiêu đề "Chuẩn bị" cùng 3 trong số: tên các mục bên phải (Trang bị, Phù hiệu, Ngọc, Phụ trợ), nút "Thêm hướng dẫn", nút "Ph.án khác". Khung Chọn tướng hay danh sách Đề cử Pro đè lên Chuẩn bị vẫn không bị nhầm.
- **Tiếp tục không đi tiếp:** bấm Tiếp tục thì app về sảnh, mở Trang bị (game mở lại Chuẩn bị của Max), rồi lần đọc kế tiếp lại không nhận ra Chuẩn bị nên dừng ngay: "Mở màn Chuẩn bị hoặc Chọn tướng trước khi thu thập Pro". Giờ app đọc lại tới 3 lần trước khi dừng.
- **"Mất lưới khi cuộn tìm tướng" ở tướng 56:** hai lần dừng trong khi ảnh lưu lại cho thấy lưới tướng nguyên vẹn; chỉ một lần đọc chữ bị trượt. Giờ app đọc lại tới 3 lần sau mỗi lần cuộn, ghi "Pro: chưa đọc rõ lưới sau khi cuộn; đọc lại".

## v0.13.57: Kiểm thử chạy sạch trên máy Windows

App trên điện thoại chạy như v0.13.56; bản này chỉ để bộ kiểm thử chạy trên Windows không còn 7 lỗi cũ, nên lỗi thật mới không bị lẫn vào đó.

- **Ghi đè tệp tiến độ và ảnh:** các chỗ lưu tệp `.part` rồi đổi tên thành tệp thật giờ thay tệp cũ trong một bước (`moveOnto`). Trên điện thoại đổi tên vốn đã thay được tệp cũ; Windows thì từ chối, nên lần lưu thứ hai luôn lỗi trong kiểm thử.
- **Kiểm thử symlink:** Windows chỉ cho tạo symlink khi bật Developer Mode hoặc chạy quyền admin. Các kiểm thử này giờ được bỏ qua trên Windows khi không tạo được symlink, thay vì báo lỗi; trên Linux (CI) vẫn chạy và vẫn phải đạt.

## v0.13.56: Tiếp tục đúng chỗ sau khi lỡ tắt màn hình hay tắt máy

Theo lượt Đề cử Pro bị ngắt giữa chừng vì lỡ bấm nút nguồn: mở lại màn hình, bấm tiếp thì lượt chạy lại từ tướng đầu.

### Vì sao Đề cử Pro chạy lại từ đầu
- Lượt Pro chỉ có một bước, nên khi tạm dừng nút chính vẫn ghi "Chạy" chứ không phải "Tiếp tục". Chọn lại Đề cử Pro trong danh sách tác vụ thì app kết thúc lượt dở và tạo lượt mới, từ tướng 1.
- Tướng nào lúc chọn không đọc được tên thì tệp tiến độ Pro ghi tên rỗng. Lần Tiếp tục sau, app coi tệp đó là hỏng và dừng với "Không đọc được tiến độ Pro". Lượt không đi tiếp được nữa, chỉ còn cách tạo lượt mới.
- Mỗi lần Tiếp tục, lượt Pro về sảnh trước, nhưng tướng dở lại được làm tiếp ngay trên màn đang hiện. Màn đó có thể là sảnh (lần nào cũng báo lỗi), hoặc màn Chuẩn bị của một tướng khác (combo chụp nhầm vào thư mục tướng dở).
- Cài bản app mới khi lượt một bước đang dở: app tạo lại macro, lượt mất chỗ đang chạy.

### Đã sửa
- Lượt đang dở (chưa bấm Dừng) luôn hiện nút "Tiếp tục", kể cả lượt chỉ có một bước như Đề cử Pro, Cờ, Chiến khu, Thống kê.
- Chọn lại đúng tác vụ của lượt đang dở thì giữ lượt đó: "Lượt … đang dở: bấm Tiếp tục để chạy tiếp từ chỗ đã dừng". Muốn làm lại từ đầu: Tùy chọn → Tạo lượt mới.
- Cập nhật app không còn tạo lại macro của lượt đang dở.
- Màn hình tắt (nút nguồn) thì app tạm dừng ngay, trước khi kịp chạm vào màn khóa hay đọc hụt màn hình giữa một tướng: "Màn hình đã tắt nên tạm dừng…".
- Đề cử Pro: tệp tiến độ có tướng không đọc được tên vẫn đọc được. Tướng dở chỉ được làm tiếp khi màn Chuẩn bị đang hiện đúng tướng đó (so tên đã đọc lúc chọn). Nếu không, app chọn lại tướng theo vị trí trong Chọn tướng rồi làm tiếp từ đề cử còn thiếu, giữ các ảnh đã chụp. Nhật ký ghi "Pro: chọn lại tướng N (pro-…), làm tiếp từ đề cử K".
- Thống kê và Chiến khu trước chỉ nhớ tiến độ trong bộ nhớ: tạm dừng thì còn, tắt máy thì mất và chạy lại cả bước. Giờ các bảng đã xong, tướng tiếp theo và người chơi đã chụp được ghi vào `step-progress.json` trong thư mục lượt.
- Thông tin tướng (theo bước), Cờ và Mời sự kiện vốn đã ghi tiến độ ra tệp. Bước đang dở khi bị ngắt vẫn làm lại từ đầu bước đó, ví dụ chiêu của tướng đang chụp.


## v0.13.55: Chụp đủ chiêu dạng khác của Tachi, Aoi, Edras; không lấy trùng tướng khi gõ tên gần giống

Sửa theo lượt `tuong-v5` (8 tên: Edras, Aya, Superman, Tachi, Thane, Yan, Aoi, Supenman; 10/11, 8 phút 47 giây).

### Nút đổi dạng ↻ bị đèn lồng che (Tachi, Aoi)
- Trang của Tachi và Aoi có đèn lồng giấy bay ở nền. Lúc app đọc hàng chiêu, đèn lồng nằm ngay sau nút ↻. Nút trắng xanh nhạt trên đèn vàng sáng ngang nhau, nên điểm khớp hình chỉ còn 0,41 (ngưỡng 0,75). App tưởng hai tướng này không có dạng khác và bỏ qua chiêu dạng 2.
- App giờ chấm nút ↻ trên độ sáng và cả kênh xanh lam, lấy điểm cao hơn. Đèn lồng vàng gần như không có xanh lam, còn nút thì có. Trên 370 ảnh trang tướng của 39 tướng qua các lượt v3, v4, v5: tướng có nút ↻ đạt từ 0,86 trở lên, tướng không có nút tối đa 0,36.

### Hàng chiêu dạng 2 của Edras không đọc được
- Sau khi bấm ↻, ô thứ 4 xanh tối của Edras dạng 2 chấm 0,54–0,63 so với ngưỡng 0,6. Lượt v4 vừa đủ qua, lượt v5 hụt (0,58). App bấm ↻ rồi ghi "Không đọc được hàng chiêu sau khi bấm đổi dạng" và không chụp chiêu nào của dạng 2.
- Ngưỡng nhận hàng chiêu hạ xuống 0,5. Màn không có hàng chiêu (tướng đứng một mình) chỉ chấm tối đa 0,21.
- Sau khi bấm ↻, nếu chưa thấy hàng chiêu, app nhìn lại tối đa 3 lần, cách nhau 1 giây, như lúc mở trang tướng.

### Tên gõ gần giống làm lấy trùng tướng
- Danh sách tên có cả "Superman" và "Supenman". Tên đọc sai một chữ vẫn được coi là cùng tướng, nên khối thứ hai cũng nhận ô của Superman và chụp lại cả tướng (thư mục `superman-2`).
- App giờ bỏ tên trùng gần đúng ngay khi tạo lượt chạy. Một lượt đã tạo từ trước cũng không lấy lại tướng đã lấy. Keera và Veera vẫn là hai tướng.


## v0.13.54: Thoát chế độ chụp ảnh tướng khi lỡ chạm trúng tướng; kéo giữ mô tả chiêu trong vùng chữ

Sửa theo lượt `tuong-v4` (35 tướng theo tên, 10/11: lấy đủ 35 tướng trong 32 phút).

### Chạm trúng tướng mở chế độ chụp ảnh (Thane)
- Để đóng bảng chiêu hay bảng thuộc tính, macro chạm "chỗ trống" ở (0,6; 0,82): ba lần mỗi tướng, một lần sau khi mở tướng và hai lần sau bảng thuộc tính. Điểm này nằm trong vùng mô hình tướng, ngay cạnh chân phải của Thane. Khi không có bảng nào đang mở, cú chạm trúng tướng và game chuyển sang chế độ chụp ảnh: chỉ còn mũi tên quay lại và nút máy ảnh.
- Lượt này Thane bị từ ảnh tổng quan: cả 14 ảnh đều là tướng đứng một mình, các ô chiêu bị chạm vào chỗ không có gì, rồi app sang tướng sau.
- App giờ nhận ra màn này: không có chữ "Chi tiết" ở góc trên trái, và có nút máy ảnh ở giữa mép phải. Trên 332 ảnh trang tướng của lượt, 14 ảnh của Thane khớp, 318 ảnh còn lại không khớp.
- Sau mỗi cú chạm "chỗ trống", app chụp kiểm tra. Nếu đang ở chế độ chụp ảnh thì bấm mũi tên quay lại một lần để về trang tướng, rồi làm tiếp. Lúc bắt đầu đọc chiêu, app cũng kiểm tra lại như vậy. Nhật ký ghi "Chạm trúng tướng nên game mở chế độ chụp ảnh tướng; bấm quay lại về trang tướng".
- Mỗi cú chạm "chỗ trống" chờ thêm khoảng 0,6 giây cho ảnh kiểm tra, để màn đã chuyển xong.

### Biron chiêu 1 vẫn thiếu chữ cuối
- v0.13.53 kéo giữ từ 0,44 lên tới 0,10, tức ra khỏi khung chữ, lên tới tên chiêu. Lượt này Biron chiêu 1 không có ảnh thứ hai.
- Giờ app kéo giữ trong khung chữ: từ 0,44 lên 0,25.
- Phần bị che ở Biron chỉ là một chữ ("nửa."), mà ngưỡng "có chữ mới" cũ tính theo cả dòng dài. Giờ một chữ ngắn cũng đủ.
- Nhật ký ghi kết quả mỗi lần kéo giữ: "thấy thêm chữ bị che; đã chụp", "chữ dịch lên nhưng không lộ thêm chữ", hoặc "chữ không nhúc nhích". Nếu là "không nhúc nhích" thì game không cho kéo quá cuối khung, và app không có cách nào lấy được dòng đó.

### Kiểm tra
- Vùng tiêu đề và nút máy ảnh cắt từ ảnh của Thane (chế độ chụp ảnh) và Arduin (trang tướng): chỉ Thane được nhận là chế độ chụp ảnh.
- Trang tướng giả đang ở chế độ chụp ảnh: app bấm quay lại trước rồi mới đọc chiêu, chụp đủ 4 chiêu.
- Ảnh khung mô tả Biron chiêu 1: khi một chữ ngắn lộ ra lúc kéo giữ thì giữ ảnh; khi chữ chỉ dịch lên thì bỏ.
- Chưa chạy trên game thật.

## v0.13.53: Quét danh sách đọc được tên tới cuối; chụp dòng mô tả chiêu bị khung che; Keera không còn bị nhận thành Veera

Sửa theo lượt `tuong-v3` (35 tướng theo tên, 10/11).

### Vì sao lượt quét lướt hết danh sách mà ít chụp tướng
- App tính các hàng tướng cách nhau 450 px, trong khi trên máy bạn là 446,5 px (đo trên ảnh lượt này, từ hàng 0 tới hàng 17). Mỗi hàng lệch thêm 3,5 px, nên tới hàng 13 tên nằm cao hơn chỗ app chờ 0,04 màn hình.
- v0.13.52 chỉ nhận chữ lệch tối đa 0,03 so với dòng tên, nên từ ô 71 app bỏ hết tên đọc được. Lượt quét đi tới cuối danh sách mà không thấy ai (Yan, Bolt Baron ở ngay màn có Edras). Lần tìm lại sau đó cũng bỏ qua tên vì lý do này, dù Aleister, Aya, Wiro, Zip đều nằm trên màn.
- Giờ khoảng cách hàng là 446,5 px. Thêm vào đó, app không so từng tên với vị trí tính sẵn, mà lấy dòng mà phần lớn tên trên màn cùng nằm. Tên lệch dòng tới 0,15 màn hình vẫn được nhận đúng ô, còn "Chưa có", dòng UID và chữ trên ảnh tướng thì vẫn bị bỏ.
- Độ lệch đo được trên mỗi màn được dùng để chỉnh lại vị trí danh sách mà app đang đếm, nên sai số từ các lần kéo không cộng dồn qua cả lượt.

### Keera bị lấy thành Veera
- Hai tên dài từ 5 chữ cái mà chỉ khác một chữ thì app coi là một (để chịu được lỗi đọc như "Kahlij." cho Kahlii). Vì thế Keera khớp với Veera.
- Giờ hai tên phải trùng 2 chữ cái đầu mới được coi là một: Keera và Veera là hai tướng, còn "Supenman" vẫn khớp Superman.

### Mô tả chiêu bị cắt dòng cuối (Biron chiêu 1, D'Arcy chiêu 2)
- Với những chiêu này, khung mô tả vừa khít chữ, đặt ngay trên bảng chỉ số, nhưng game tính chiều cao khung thiếu một dòng. Biron chiêu 1 dừng ở "…hồi máu giảm một", D'Arcy chiêu 2 dừng ở "…<Lập phương thứ". Game coi như chữ đã vừa khung nên cuộn không nhúc nhích, và app chỉ giữ một ảnh.
- Các chiêu dài có nút "Chú thích" (Allain chiêu 3, Aoi chiêu 2) vẫn cuộn tới hết chữ như trước.
- Giờ khi lần cuộn không làm chữ di chuyển, app kéo chữ lên quá cuối khung, giữ tay và chụp trong lúc giữ. Ảnh đó chỉ được giữ (thành `…-detail-2`) nếu có chữ mới không có trong ảnh trước. Nếu chữ chỉ dịch lên mà không có gì mới thì app bỏ ảnh. Mỗi chiêu như vậy tốn thêm khoảng 2 giây.
- Chưa thử trên game thật. Nếu game không cho kéo quá cuối khung thì sẽ không có ảnh thứ hai. Khi đó nhật ký không có dòng "kéo giữ quá cuối khung thấy thêm chữ bị che".

### Kiểm tra
- ML Kit thật (chạy trên máy ảo) với 4 ảnh danh sách của lượt này, đã che UID: trang đầu, trang có Yue, trang có Edras, trang có Aleister. App đọc đúng ô cả 40 tên, kể cả khi cố ý cho vị trí danh sách lệch ±0,05 màn hình.
- Danh sách giả 130 tướng có dòng tên lệch 0,0033 màn hình mỗi hàng, về cả hai phía: lấy đủ 35 tướng tới cuối danh sách.
- Ảnh khung mô tả Biron chiêu 1: có dòng mới lộ ra khi kéo giữ thì giữ ảnh; chữ chỉ dịch lên thì bỏ.
- Kiểm thử trên máy (UiSmoke) biên dịch lại được; v0.13.52 để lỗi biên dịch ở phần lấy tướng theo tên.

## v0.13.52: Lấy thông tin tướng theo tên bằng một lượt quét, không dựa vào vị trí đã lưu

v0.13.51 xếp thứ tự tướng theo vị trí đã lưu trong `hero-index.tsv`. File này dùng chung cho cả máy, không theo tài khoản hay cách xếp, nên vị trí từ tài khoản khác (xếp theo Thông thạo) sẽ sai.

### Cách quét
- App đi từ đầu danh sách xuống đúng một lần. Ở mỗi màn hình dừng lại, app đọc tên trên thẻ (ML Kit). Gặp tướng cần lấy thì bấm, chụp, quay lại danh sách, rồi đọc lại màn đó để lấy tướng khác cùng màn.
- Không còn tướng cần lấy trên màn thì app kéo tiếp 60% màn hình. Một màn đọc được tên của khoảng 80% chiều cao, nhiều hơn một lần kéo, nên không hàng nào bị bỏ qua.
- Hàng nào đã đọc đủ 5 tên thì không đọc lại, trừ khi trên đó còn tướng cần lấy.
- Thứ tự lấy là thứ tự trên danh sách, không phải thứ tự bạn gõ. `hero-001` là tướng gặp đầu tiên; thư mục ảnh vẫn mang tên tướng.
- Biết tổng số tướng (số "130/130" trên đầu danh sách) thì đọc hết hàng cuối là xong, không phải kéo thêm để thấy danh sách hết.
- Cả danh sách 130 tướng dài khoảng 9,9 màn hình, tức khoảng 17 lần kéo, bất kể lấy bao nhiêu tướng. Lượt trước kéo 64 lần mà mới lấy được 9 tướng.

### Tướng app không đọc được tên
- Đi hết danh sách mà còn tướng chưa gặp: nhật ký ghi "Đã đi hết danh sách; chưa thấy: …".
- Mỗi tướng còn lại được tìm **một lần** ở ô đã đọc được tên nó trong lượt quét, hoặc ô đã lưu từ trước. Không có ở đó thì app ghi "Không thấy … trong danh sách; bỏ qua tướng này" và làm tướng sau. Không dò lại cả danh sách.

### Tạm dừng rồi Tiếp tục
- Tướng từng khối đã lấy, các tên đã đọc trên từng ô và chỗ quét tới được ghi vào `sweep.json` của lượt.
- Dừng giữa lúc đang chụp một tướng thì Tiếp tục lấy lại đúng tướng đó, để ảnh không lẫn sang thư mục khác.

### Tên đọc sai trên thẻ
- Ở tài khoản có tướng chưa sở hữu, dưới tên game ghi "Chưa có". Trước đây app nhận chữ này là tên tướng và ghi đè vào vị trí đã lưu. Dòng UID mờ ở đáy màn hình cũng có thể bị nhận nhầm như vậy.
- Giờ app chỉ nhận chữ nằm đúng dòng tên (lệch tối đa 3% chiều cao), bỏ "Chưa có" và dòng UID.

### Kiểm tra
- Danh sách giả 130 tướng, 35 tướng của lượt trước theo thứ tự bạn gõ, vị trí đã lưu sai hết: lấy đủ, theo thứ tự trên danh sách, tối đa 18 lần kéo.
- Dừng sau khối 4 rồi chạy lại (app mất vị trí danh sách): khối 4 lấy lại đúng tướng cũ, các khối sau đi tiếp.
- Tướng không đọc được tên lúc quét: tìm thấy ở ô đã lưu. Tướng không có trong danh sách: bỏ qua.
- Chữ "Chưa có" và dòng UID không bị nhận là tên.
- Chưa chạy trên máy thật.

## v0.13.51: Lấy thông tin tướng theo tên: đi danh sách một lượt từ trên xuống, không dừng giữa chừng

Sửa theo lượt `lay-danh-sach-tuong` (35 tướng theo tên, dừng sau tướng thứ 9).

### Vì sao lượt dừng
- Sau Bijan (ô 102 trên 130), app đi tìm Biron ở ô 1. Mỗi lần tìm, app chỉ được kéo 16 lần cộng số hàng tới ô cần tìm, tức 16 lần với ô 1. Từ ô 102 lên đầu cần khoảng 18 lần, nên app dừng với "Không cuộn tới được ô tướng 1" khi còn cách đầu chưa tới một màn hình.
- Giờ số lần kéo được phép tính theo quãng đường từ chỗ danh sách đang đứng tới ô cần tìm: gấp đôi số lần cần, cộng 6, và không ít hơn mức cũ.
- Hết số lần kéo mà chưa tới, app về đầu danh sách đếm lại một lần rồi mới tạm dừng.

### Vì sao chậm và cách đi mới
- **Thứ tự đi:** trước đây app đi theo thứ tự bạn gõ, nên kéo lên kéo xuống giữa các tướng (Edras ô 69, rồi Aleister ô 89, Allain ô 38, Aoi ô 95…).
  - Với 35 tướng của lượt này là khoảng 187 lần kéo, mỗi lần khoảng 3,7 giây: hơn 11 phút chỉ để kéo.
  - Giờ app xếp các tướng theo ô đã biết, từ trên xuống, và đi danh sách đúng một lượt. Cùng 35 tướng: khoảng 21 lần kéo và một lần vuốt lên đầu.
  - Tướng chưa biết vị trí đi sau cùng. App dò từ chỗ danh sách đang đứng xuống cuối, rồi ngược lên, không quay về đầu. Tên đọc được trên đường đi đều được lưu, nên tới lượt thì nhiều tướng đã có vị trí.
  - Thư mục ảnh vẫn mang tên tướng. Chỉ số `hero-001`, `hero-002`… theo thứ tự đi.
- **Mỗi lần kéo dài hơn:** 60% màn hình thay vì 45%.
  - Đã thử trên ảnh danh sách của lượt này: dịch tới 780 px vẫn đo đúng từng điểm ảnh.
  - Kéo có giữ ngón đi đúng bằng ngón tay: 476–487 px cho lần kéo 486 px.
- **Ô ở gần đầu mà danh sách đang ở xa bên dưới:** app vuốt nhanh lên đầu rồi kéo xuống, thay vì kéo ngược từng đoạn. Chỉ danh sách tướng làm vậy; Cờ đi từng thẻ từ trên xuống như cũ.
- **Không lưu ảnh mọi trang lướt qua:**
  - Mỗi trang là một PNG khoảng 2,2 MB. Ảnh được ghi trên cùng luồng với bước đo lần kéo sau, nên bước đo có thể phải chờ.
  - Lượt này có 65 ảnh như vậy, chiếm phần lớn trong 262 MB của ZIP.
  - Giờ danh sách tướng chỉ lưu trang có thẻ được bấm. Cờ vẫn lưu mọi trang như cũ.

### Lượt đang tạm dừng
- Bấm Tiếp tục vẫn chạy được, theo thứ tự cũ, với cách tính số lần kéo mới.
- Nhanh hơn nhiều: tạo lượt mới với 26 tướng còn lại (từ Biron tới Xeniel). App tự xếp thứ tự.

### Kiểm tra
- Danh sách giả 130 tướng:
  - Từ ô 102 tới ô 1 chỉ bằng vuốt nhanh, không kéo ngược.
  - Không có đường tắt lên đầu (như Cờ) mà danh sách chỉ đi nửa đường ngón tay: kéo ngược hơn 16 lần vẫn được phép, tới được ô 1.
  - Từ đầu tới ô 102 tối đa 15 lần kéo.
  - Tướng chưa biết vị trí được tìm từ chỗ đang đứng, cả khi tướng ở bên dưới lẫn bên trên.
  - Danh sách tướng chỉ lưu trang có thẻ được bấm; danh sách Cờ vẫn lưu mọi trang.
- Macro 6 tướng xếp đúng thứ tự: ô 1, 38, 72, 89, 102, rồi tướng chưa biết.
- Chưa chạy trên máy thật.

## v0.13.50: Chiến khu sao chép tên và UID; Đề cử Pro không vuốt về đầu mỗi tướng; Cờ không lưu trùng Chúc Phúc

Sửa theo ba lượt bạn gửi (chienkhu, co-v2, de-cu-pro) và ảnh hộp UID.

### Chiến khu: sao chép tên và UID thay cho ảnh đại diện
- App không bấm ảnh đại diện nữa. Sau khi chụp **Thông tin kiện tướng**, với mỗi người app làm:
  1. Bấm **biểu tượng sao chép tên** (góc trên bên phải bảng trái) và đọc tên game vừa chép.
  2. Bấm **con mắt** bên cạnh. Hộp thông tin hiện ra thì chụp: `hero-001-quoc-gia-hang-01-uid`.
  3. Bấm **biểu tượng sao chép UID** ngay sau dãy số và đọc UID vừa chép.
- Tên và UID ghi vào `players.json` của lượt, theo từng người: `{"hero-001-quoc-gia-hang-01": {"hero": "Richter", "name": "…", "uid": "…", "uidSource": "copy"}}`.
- **Đọc bộ nhớ tạm:** từ Android 10, chỉ ứng dụng đang được chọn mới đọc được bộ nhớ tạm. Vì vậy app mở một cửa sổ 1 điểm ảnh của dịch vụ Trợ năng để đọc, rồi gỡ ngay và trả lại game. Android 12 trở lên có thể báo nhỏ "đã dán từ bộ nhớ tạm".
  - Máy không cho đọc thì tên để trống, còn UID lấy từ chữ đọc được trong hộp (`"uidSource": "ocr"`). App không đoán tên.
  - Trước mỗi lần sao chép, app xoá bộ nhớ tạm, nên tên hay UID của người trước không bị gán nhầm cho người sau.
- Con mắt không mở hộp thì app bỏ qua UID của người đó, không bấm mò vào chỗ hộp lẽ ra hiện. Biểu tượng sao chép UID được tìm bằng điểm ảnh (đo ở 56,6% chiều ngang trên ảnh bạn gửi).
- Đường quay lại: app nhìn màn hình từng bước. Hộp UID có thể nhận lần bấm mũi tên đầu tiên, nên app không bấm mò ra ngoài thẻ như trước.

### Đề cử Pro: không vuốt xuống rồi kéo lên lại mỗi tướng
- Trong lượt bạn gửi, mỗi tướng mất 3–4 phút. Mỗi lần mở Chọn tướng, app vuốt thử 30% màn hình (danh sách trôi xa), rồi kéo lên từng đoạn 24% chậm, mỗi đoạn một lần nhìn.
- Giờ Đề cử Pro dùng cách của Chiến khu:
  - Vuốt thử chỉ 10% màn hình. Về đầu bằng những lần lướt nhanh, hai lần lướt không làm danh sách nhích là tới đầu.
  - App nhớ hình đầu danh sách và chỗ đã chọn tướng trước. Lần mở sau, nếu Chọn tướng còn ở chỗ cũ hoặc mở ở đầu danh sách, app đi thẳng tới tướng cần chọn, không vuốt thử, không về đầu. Nhật ký ghi "Chọn tướng còn ở chỗ lượt trước" hoặc "mở ở đầu danh sách như lần trước".

### Bách khoa Cờ
- **Tất cả quân cờ: không phải đếm lại từ đầu.** Ở ô 36, một lần kéo 413 px bị đo thành 650 px, đúng mép vùng so ảnh (phần hai ảnh còn trùng nhau rất ít nên khớp nhầm). App tìm ô 36 sai chỗ, kéo kiểm tra thấy còn thẻ rồi đếm lại từ đầu, mất hơn một phút. Lần kéo có giữ ngón không làm danh sách trôi xa hơn ngón tay, nên giờ app chỉ tìm độ cuộn trong tầm đó (thêm 8% màn hình).
- **Chúc Phúc: không bấm và lưu lại thẻ đã lưu.** Mỗi lần kéo, danh sách đi khoảng 59% màn hình. Thẻ ở đáy trang trước hiện lại ở đầu trang sau, bị thanh tab che mất một phần hình. Trước đây app so theo hình nên không nhận ra, và lưu trùng (Thường số 11, 12 là 9, 10; số 25 là 22).
  - Giờ app đo mỗi lần kéo bằng ảnh và nhận thẻ theo chỗ nó được kéo tới.
  - Ba Chúc Phúc Truyền thuyết dùng chung một hình (Lãng khách, Cuồng chiến sĩ, Đêm đông cổ tích) không còn bị coi là một.
  - Không đo được thì app so theo hình như cũ và ghi vào nhật ký.

## v0.13.49: Chiến khu không dừng ở Chọn tướng; Đề cử Pro lấy đủ combo; Cờ không bỏ dở Tất cả quân cờ

Sửa theo hai lượt bạn gửi (ZIP) và ảnh Tất cả quân cờ.

### Chiến khu
- **Dừng vô lý ở Chọn tướng.** Nhật ký báo "Chưa thấy hàng đầu đủ ảnh tại vị trí chuẩn" dù danh sách đang ở đầu. Game hiện đặt hàng tướng đầu ở 29,4% chiều cao, còn app chỉ nhận 32–37% (đo trên một ảnh cũ). Giờ app nhận từ 28,9%; hàng bị mép trên cắt vẫn không được tính.
- **Lướt mạnh xuống rồi lướt về đầu rất lâu.**
  - Lần vuốt thử chiều cuộn ngắn lại (10% màn hình thay vì 30%), nên danh sách không còn trôi xuống tận đáy.
  - Khi đã lướt ngược lên được một lần, chỉ cần thêm một lần lướt không làm danh sách nhích là coi như tới đầu (trước cần hai lần).
  - App nhớ hình đầu danh sách đã xác minh. Những lần mở Chọn tướng sau, game mở ở đầu danh sách, app nhận ra ngay và không vuốt thử, không lướt về đầu nữa.

### Đề cử Pro
- **Còn nút Dùng combo mà không làm tiếp.** Trên ảnh thật 2400×1080 của máy bạn, nửa trên nút "Dùng combo" có màu đỏ 200, còn app đòi trên 200. Nút bị tách làm hai mảnh vàng ngắn quanh chữ, app không thấy nút nào, mất dấu danh sách sau combo 2 rồi bỏ luôn các combo còn lại.
  - Ngưỡng màu nới ra theo màu thật của máy (đỏ trên 175). Ảnh lúc mất dấu trong ZIP (đã che tên, ảnh người chơi và UID) giờ nhận ra đúng một nút.
  - Nếu vẫn mất dấu khi cuộn, app về đầu danh sách và đếm lại một lần, rồi mới bỏ qua.

### Bách khoa Cờ: Tất cả quân cờ khi mở từng quân cờ
- Ảnh của bạn cho thấy app dừng ngay sau một lần kéo từ hàng 3 vàng xuống hàng sau, dù hàng sau đã hiện.
- **Chờ danh sách đứng yên:** sau mỗi lần kéo, app nhìn lại cho tới khi hai ảnh liền nhau giống nhau mới đo khoảng cuộn. Khi game bận tải hình hàng mới, lần kéo có thể hiện trễ; đo sớm thì mọi ô sau bị tính sai chỗ.
- **Kiểm tra trước khi coi là hết:** khi không thấy thẻ ở chỗ cần bấm, app kéo danh sách lên một đoạn. Nếu danh sách còn đi gần trọn quãng kéo và phần dưới màn hình còn thẻ, app đếm lại từ đầu danh sách (tối đa 2 lần) thay vì sang Phe & Nghề.

Chưa thử trên game thật.

## v0.13.48: Bách khoa Cờ lấy hết Tất cả quân cờ; Trang bị chờ hình tải xong rồi mới chụp

Sửa hai lỗi của **Chụp nhanh** trong Bách khoa Cờ, tìm ra trên hai ảnh bạn gửi:

- **Tất cả quân cờ dừng giữa danh sách.**
  - **Nguyên nhân:** app coi dải đáy màn hình là nền trơn tức là trang đã hết. Thẻ quân cờ 1 vàng màu xám nên dải đáy luôn có viền thẻ. Từ 3 vàng trở lên, thẻ xanh và tím gần như cùng màu nền, và phần dưới tên, dưới phe/nghề là màu trơn. Khi dải đáy rơi đúng vào phần đó, app tưởng hết danh sách và dừng ở hàng 3 vàng.
  - **Sửa:** ở Tất cả quân cờ, app không dùng dải đáy nữa. Danh sách chỉ hết khi kéo mà trang không đổi, tốn thêm 1 lần kéo ở cuối.
  - **Khi bật mở từng quân cờ:** nếu ở chỗ có thẻ mà chưa thấy chân dung, app chờ rồi nhìn lại một lần trước khi kết luận hết danh sách, phòng khi hình chưa tải.
  - Sau mỗi lần kéo, app chờ 0,5 giây (trước là 0,3 giây) cho danh sách đứng yên rồi mới chụp.
- **Trang bị chụp khi hình chưa tải.**
  - Game vẽ ô vuông sáng có hình thoi xám trước, tải xong mới thay bằng hình trang bị. App giờ đếm các ô này và chờ, tối đa 8 lần nhìn (khoảng 10 giây). Quá thời gian thì vẫn chụp và ghi vào nhật ký. Trên ảnh của bạn app đếm đúng 15 ô chưa tải; trên ảnh đã tải xong thì không đếm ô nào.
  - **Trang đầu của mọi tab** chỉ được chụp khi hai lần nhìn liên tiếp giống nhau, vì game vẽ hình của tab mới mở chậm hơn một nhịp. Mỗi tab tốn thêm khoảng 1 giây.
  - **Bảng chi tiết** (quân cờ, phe/nghề, trang bị) chờ hình tròn ở góc trên được vẽ tối đa 5 lần nhìn thay vì 2.

Chưa thử trên game thật.

## v0.13.47: nhập mã sự kiện dạng chung (ví dụ Ủy Thác của Shuna); danh sách tướng chụp đủ chiêu khi máy lag

### Nhập mã sự kiện nhiều tài khoản

Tác vụ trước chỉ làm được Thập kỷ chung sức. Giờ nó tách thành **phần chung** và **phần riêng của từng sự kiện**:

- **Phần chung**, dùng cho mọi sự kiện:
  - Với từng tài khoản: đăng xuất, đăng nhập, vào Sự kiện.
  - Tìm sự kiện theo tên trong danh sách bên trái; chưa thấy thì kéo danh sách.
  - Đóng popup quà và hướng dẫn trên đường đi và sau khi nhập.
  - Ghi sổ để không bao giờ gửi mã hai lần, rồi quay về sảnh.
- **Phần riêng** chọn trong form:
  - **Thập kỷ chung sức:** giữ nguyên như cũ.
  - **Ủy Thác của Shuna:** tab Nhiệm vụ chia sẻ → ô "Nhập mã để nhận nhiệm vụ…" → Dùng.
  - **Sự kiện khác:** sự kiện mới có ô nhập mã thì gõ tên sự kiện, tên tab, chữ trong ô và tên nút như trong game, không cần sửa app.
- **Sự kiện Shuna:**
  - Đọc số lượt trên tab ("0/2"); đã đủ thì bỏ qua tài khoản đó.
  - Sau khi bấm Dùng, chụp kết quả: popup quà, chữ báo lỗi, hoặc số lượt tăng.
- Chi tiết: `docs/EVENT_CODES.md`.

### Danh sách tướng: chiêu dài và máy lag

- **Mô tả chiêu dài giờ được kéo đủ** (ví dụ chiêu 2 của Billow).
  - **Nguyên nhân:** bản cũ chỉ xem một dải hẹp ở y .445–.468, đo trên chiêu của Dyadia, và chỉ tính chữ sáng. Ở Billow, dải đó chỉ có dòng chữ xám "(giảm nửa với đơn vị không phải tướng)", còn khung chữ kéo xuống tới khoảng y .53. Vì vậy app tưởng mô tả đã hết.
  - **Cách sửa:** giờ app xem tới y .545 và tính cả chữ xám.
- **Chờ chữ hiện trước khi chụp Chi tiết:** app nhìn khung chữ trước khi chụp. Chưa có chữ (panel còn đang mở, máy lag) thì chờ thêm, tối đa 3 giây. Lần nhìn này là lần đã có sẵn để quyết định có kéo hay không, chỉ chuyển lên trước ảnh, nên không tốn thêm thời gian.
- **Chờ trang tướng tải xong:**
  - Sau khi mở một tướng từ danh sách, app chờ tới khi hàng chiêu đọc được và giống nhau ở 2 lần nhìn, rồi mới chạy các bước chụp tổng quan, thông số, chiêu. Tối đa khoảng 10 giây; quá thì vẫn chụp và ghi nhật ký.
  - Bước chiêu đọc lại hàng chiêu tới 3 lần trước khi bỏ cuộc và chụp theo 4 vị trí đoán sẵn.

Chưa thử trên game thật. Hai ảnh Billow và ảnh trang sự kiện Shuna bạn gửi không lưu lại được trên máy, nên phần đo dựa trên tọa độ đọc từ ảnh. Kiểm thử dùng màn hình dựng lại theo các tọa độ đó.

## v0.13.46: Chiến khu mở từng người; Đề cử Pro nhanh hơn và chờ combo hiện xong; chọn số người, số combo

**Không còn ô "Số tướng".** Chiến khu và Đề cử Pro luôn chạy hết tướng trong Chọn tướng. Macro cũ có lưu số tướng cũng chạy hết. Cả hai đều bỏ tích **Đã nhận** mỗi lần mở Chọn tướng. Phần này đã kiểm thử trên ảnh hai bảng Chọn tướng bạn gửi: app thấy ô đang tích và biết chỗ bấm để bỏ.

### Chiến khu

- **Số người top mỗi bảng do bạn chọn** (Top 3, Top 10, Hết danh sách hoặc gõ số), thay cho 3/10 cố định. Số này áp dụng cho Tỉnh, Miền, Việt Nam, Toàn máy chủ. "Số 1 máy chủ" vẫn chụp 1 ảnh.
- Ô mới **Bấm từng người trong top** (bật sẵn). Ngoài ảnh danh sách như trước, app làm lần lượt với từng người trong top:
  1. Bấm vào dòng của người đó, rồi bấm **biểu tượng người** trên thẻ hiện bên phải.
  2. Chụp **Thông tin kiện tướng**: `hero-001-quoc-gia-hang-01-ho-so`.
  3. Bấm ảnh đại diện, chụp **ảnh lớn**: `hero-001-quoc-gia-hang-01-anh-dai-dien`.
  4. Quay lại bảng, kiểm tra danh sách còn đúng chỗ rồi mới mở người tiếp theo.
- Nút Kết bạn và Báo cáo trên thẻ không bao giờ được bấm.
- **Chờ dữ liệu tải xong:**
  - Hồ sơ chỉ được chụp khi bảng thông tin bên trái giống hệt ở 2 lần nhìn liên tiếp và đã có số trận, tỷ lệ thắng, ảnh đại diện.
  - Ảnh lớn chỉ được chụp khi đã hiện hình và không còn đổi.
  - Mạng chậm thì app chờ, tối đa khoảng 25 giây cho hồ sơ và 18 giây cho ảnh. Quá thời gian đó app vẫn chụp và ghi chú "tải lâu" vào nhật ký lượt.
- **Đường quay lại:** mỗi bước app đọc lại màn hình. Còn ảnh lớn thì bấm X, còn hồ sơ thì bấm mũi tên, còn thẻ thì chạm ra ngoài thẻ.
  - Danh sách bị dịch hoặc về đầu: app đo lại vị trí bằng ảnh, hoặc bấm lại tab rồi tìm từ đầu.
  - Lạc hẳn: app vào lại Chiến khu và chụp tiếp tướng đó, bỏ qua người đã chụp.
- **Nhận dạng bằng điểm ảnh** (`BoardPlayerPixels.kt`), đo trên ảnh bạn gửi: các dòng (theo số hạng bên trái), thẻ, hồ sơ, ảnh lớn. OCR chỉ dùng để nhận trang Thông tin kiện tướng và khi không chắc đang ở đâu.
- **Tiếp tục sau khi dừng:** bỏ qua người đã có đủ 2 ảnh.
- **Thời gian:** mỗi người khoảng 12–15 giây, chủ yếu do chờ tải. Ví dụ top 3 thêm khoảng 40 giây mỗi tướng. Ước tính trong form đã tính phần này.
- Chi tiết: `docs/BOARD_PLAYERS.md`.

### Đề cử Pro

- **Số combo mỗi tướng do bạn chọn** (1, 3, 5, Tất cả hoặc gõ số), thay cho 5 cố định. Tướng có ít đề cử hơn thì lấy hết những gì có.
- Vẫn làm như trước: Phương án khác → Đề cử Pro → bấm **Dùng combo** từng đề cử → chụp màn Chuẩn bị → cuối mỗi tướng dùng lại đề cử 1.
- **Chụp khi combo đã hiện xong:** sau Dùng combo, app chỉ chụp khi bảng bên phải của Chuẩn bị (trang bị, phù hiệu, ngọc, phụ trợ) giống hệt ở 2 lần nhìn liên tiếp. Bản cũ chụp ngay lần nhìn đầu.
- **Nhanh hơn:**
  - Khi cuộn danh sách đề cử, app tìm nút Dùng combo bằng màu vàng của nút (`ProPixels.kt`), không OCR (bản cũ OCR hai lần mỗi ảnh, một lần ở độ phân giải gấp đôi).
  - Mỗi lần kéo đi 2/3 thẻ thay vì nửa thẻ.
- **Danh sách tải chậm:** bản cũ đọc 2 lần không thấy nút là kết luận "không có đề cử". Giờ app chờ khoảng 10 giây (20 giây nếu game báo đang tải hoặc đang kết nối), trừ khi game ghi rõ là không có đề cử.

Chưa thử trên game thật. Với Chiến khu có hai điều chưa rõ, cả hai trường hợp app đều đã xử lý:
- Bấm vào tên trên dòng có mở thẻ không. Nếu không, dạy lại điểm `board.v1.player.row`.
- Sau khi quay lại, thẻ còn mở hay danh sách có về đầu không.

## v0.13.45: Bách khoa Cờ chụp nhanh, không OCR, đặt tên theo số thứ tự

Bách khoa Cờ giờ có **Chụp nhanh** (bật sẵn, ô đầu tiên ở "Thu thập nội dung"). Đường vào game và vào Bách khoa giữ nguyên, kể cả phần tự qua popup hướng dẫn, quà và "Ghi chú cập nhật" của v0.13.44. Bên trong Bách khoa thì làm lại (`ChessQuickReader.kt`):

- **Không OCR.** Mọi thứ được đọc từ điểm ảnh (`ChessPixels.kt`), đo trên 15 ảnh Bách khoa bạn gửi: trang đã hết hay còn, bảng chi tiết đã mở và đã vẽ hình, lưới Tất cả quân cờ, hộp Save Photo, hình các thẻ Chúc phúc.
- **Đặt tên theo số thứ tự** trên trang: `quan-co-001`, `vat-pham-004`, `phe-012`, `nghe-003`, `chuc-phuc-007`. Ảnh nằm cùng thư mục như trước, ví dụ `quan-co/tat-ca-quan-co/tuong/quan-co-001/quan-co-001-detail-1.png`.
- **Ít ảnh chụp hơn.** Android chỉ cho chụp khoảng 1 ảnh mỗi giây, nên thời gian chủ yếu nằm ở số lần chụp:
  - Trang có đáy là nền trống (Thông tin cơ bản, Tương tác, Trang bị, bảng chi tiết ngắn) thì không kéo thử nữa.
  - Mỗi lần kéo đi xa hơn (60% màn hình thay vì 42%), nên ít trang hơn.
  - Kéo mà trang không đổi là hết. Chỉ lần kéo đầu của mỗi tab được thử lại một lần, thay vì 3 lần ở mọi trang.
  - Mỗi mục chỉ chụp 1 ảnh để kiểm tra bảng đã mở, không đọc tên, không chờ 3 khung ổn định.
- **Lưu ảnh chạy nền:** app chụp tiếp trong khi ảnh trước đang được ghi.
- **Ảnh gốc (Save Photo) vẫn giữ:** nhấn giữ, nhận ra hộp Save Photo bằng điểm ảnh rồi bấm **Sure**. Nếu không chắc là hộp Save Photo thì đọc OCR trước khi bấm, và chỉ bấm Sure khi đọc ra đủ "Save Photo", "Sure", "CANCEL". Hộp Save Photo hiện lên ngoài dự kiến thì bấm CANCEL, không bao giờ Sure. Việc đối chiếu ảnh trong Pictures chạy nền theo thời điểm bấm Sure, không bắt lượt chạy phải chờ.
- **Tiếp tục sau khi dừng:** bỏ qua tab đã xong, mục đã mở và ảnh gốc đã lưu theo số thứ tự. Tab đang dở được chụp lại từ trang đầu, ghi đè ảnh trang cũ.
- Bỏ chọn **Chụp nhanh** để quay về cách cũ: đọc tên bằng OCR, chậm hơn. Có thể đổi qua lại mà không cần cài lại app.

Chưa thử trên game thật. Thời gian chưa đo được trên máy thật. Ước tính theo số ảnh chụp: mỗi trang hoặc mục khoảng 1–2 giây, mỗi quân cờ có mở chi tiết khoảng 4–5 giây. Cách cũ cần khoảng 10–20 giây cho mỗi mục.

## v0.13.44: tự qua popup hướng dẫn và quà; Cờ không kẹt ở "Ghi chú cập nhật"

Lỗi trên máy bạn: sau bản cập nhật, sảnh Cờ Liên quân mở hộp **Ghi chú cập nhật**. App không biết hộp này, cứ bấm "Trang chủ" nằm dưới hộp (chạm không có tác dụng) tới khi hết lượt thử rồi dừng với "Không nhận ra đường vào Bách khoa".

App giờ nhận ra 3 kiểu thoát popup, dùng chung một bộ nhận dạng (`GamePopups.kt`):

| Kiểu | Ví dụ | Khi nào bấm |
| --- | --- | --- |
| Dòng chữ bảo chạm | "Ấn để tiếp tục", "Chạm vào vùng trống để đóng", "Nhấn để bỏ qua" | Ngay khi thấy |
| Nút X | "Ghi chú cập nhật" và các popup khác | "Ghi chú cập nhật": ngay khi thấy. Popup lạ: sau khi 2 lần bấm sảnh không có tác dụng |
| Nút bấm | "Đã rõ", "Đã hiểu", "Nhận", "Nhận thưởng", "Đóng", "Bỏ qua", "Để sau", "Hủy" | Chỉ khi màn hình không đi tiếp và không có X |

- OCR đọc sai hoặc mất **một chữ** vẫn được nhận, như "* Ấn để iếp tục" trên màn quà của bạn (bản cũ bỏ qua dòng này).
- Điểm chạm phải giữ nguyên qua 2 lần nhìn, để không bấm vào popup đang hiện dần hay hình động.
- **Bảo vệ tiền và tài khoản:** "Xác nhận", "Tiếp tục", "OK" chỉ được bấm khi đó là nút duy nhất và hộp không có chữ về mua, nạp, đổi, tiêu, Quân huy, thanh toán, đăng xuất, thoát, xóa. Có "Hủy" thì bấm "Hủy". "Đồng ý" không bao giờ tự bấm. Nhãn phải khớp nguyên dòng và nằm giữa màn hình.
- Thử đóng nhiều lần mà popup vẫn còn: tạm dừng để bạn đóng bằng tay, không bấm mãi.
- Áp dụng cho **đường vào game** của mọi tác vụ và **đường vào Bách khoa Cờ**. Sau khi đã rời sảnh, Chiến khu, Xếp hạng và Đề cử Pro chưa dùng bộ nhận dạng này.

## v0.13.43: đọc UID trên nền sảnh động

Nền sảnh mới là ảnh động: phần sáng của nền sau dòng UID đổi theo từng khung, nên một ngưỡng sáng duy nhất để tách chữ khỏi nền có lúc làm mất hoặc đổi một chữ số.

- Khi chưa đọc ra đúng UID test, app đọc lại cùng ảnh với vài ngưỡng sáng khác và một vùng cắt hẹp hơn quanh chữ (tối đa thêm 3 lần đọc). Chỉ một kết quả **trùng khớp hoàn toàn** UID test mới được nhận.
- Mỗi lần nhìn sảnh là một khung mới của nền động, và app nhìn tối đa 3 lần trước khi mở hồ sơ, nên thường xác nhận được ngay ở sảnh.
- Vẫn giữ bảo vệ của v0.13.42: đọc ra số khác không bao giờ làm đăng xuất, mà phải kiểm tra trong hộp Xem UID.

## v0.13.42: không đăng xuất vì đọc nhầm UID ở sảnh mới

Game đổi nền sảnh. Dòng UID mờ ở đáy màn hình giờ dễ bị đọc nhầm một chữ số, và bản cũ coi mọi số khác UID đã lưu là tài khoản khác nên đi đăng xuất, dù tài khoản đúng.

- Dòng UID ở sảnh giờ **chỉ dùng để xác nhận đúng tài khoản**. Đọc ra số khác thì app mở hồ sơ → **Xem UID** (chữ to, rõ) để kiểm tra, không đăng xuất ngay.
- Chỉ đăng xuất khi hộp Xem UID cho thấy một UID **khác hẳn**. Số chỉ lệch 1–3 chữ số so với UID test được coi là đọc chưa rõ: app dừng và hỏi, không bao giờ đăng xuất vì nó.
- Nhật ký ghi số đã đọc được ở sảnh và trong hộp Xem UID, để dễ xem lại.

## v0.13.41: Chiến khu không chọn nhầm tướng; Đề cử Pro chỉ chụp, đặt tên theo số

**Chiến khu:**
- Lỗi trên máy bạn: sau khi bỏ "Đã nhận", app kéo thử lưới tướng một đoạn ngắn (12% màn hình) chậm rãi rồi giữ tay. Game coi đó là **bấm vào thẻ**, chọn luôn Hayate và đóng Chọn tướng, nên app dừng với "Không đo được thay đổi vị trí lưới tướng".
- Cú kéo thử giờ là một cú **vuốt nhanh**. Mọi cú kéo chậm trong lưới tướng dài ít nhất 15% màn hình (trước đây có thể chỉ 7,5% khi tìm hàng tướng).
- Bộ test giờ mô phỏng đúng hành vi đó của game: kéo ngắn chậm là chọn tướng. Với cú kéo cũ, 15 ca thử Chiến khu thất bại; với bản mới, tất cả đều qua.

**Đề cử Pro**, viết lại theo yêu cầu "chỉ chụp, không cần nhận ra tên":
- Không đọc tên người đề cử, không lập danh mục thứ hạng, không đọc lại để so khớp. Đây là phần khiến lượt cũ dừng với "Thứ hạng/combo Pro đổi khi đọc lại".
- Đề cử được đếm theo **vị trí nút Dùng combo**: ở đầu danh sách nút đầu ở 41% chiều cao, các thẻ cách nhau 36,6%. Mỗi lần kéo nửa thẻ, vị trí được đo từ chính các nút. Đọc sót một nút không làm lệch số thứ tự.
- Mỗi đề cử (tối đa 5): bấm Dùng combo, đợi về Chuẩn bị, chụp. Cuối cùng dùng lại đề cử 1 để tướng giữ combo đứng đầu.
- Tên theo số thứ tự tướng trong danh sách: thư mục `pro-001`, ảnh `pro-001-1.png` … `pro-001-5.png`. Tên tướng đọc được (nếu có) chỉ ghi vào nhật ký. Không có đề cử: `pro-001-khong-co-de-cu.png`.
- Gặp chỗ không theo được (Dùng combo bấm 2 lần không về Chuẩn bị, mất dấu thẻ khi cuộn): ghi nhật ký, giữ ảnh đã chụp và **chuyển sang tướng tiếp theo**, không dừng lượt.
- Chọn tướng của Pro dùng cùng lưới "Chọn tướng" với Chiến khu nên có cùng lỗi kéo thử 12% chậm. Đã đổi sang vuốt nhanh, và cú kéo khi tìm hàng tướng không ngắn quá 15% (trước đây có thể chỉ 6%).

## v0.13.40: thông báo không còn che ảnh hay nhận cú chạm

**Luôn bật, cho mọi tác vụ:** khi có thông báo mới trong lúc chạy, app chờ banner thông báo ẩn rồi mới chụp màn hình hoặc chạm.
- Banner hiện ở góc trên bên phải, đúng chỗ nút X đóng của nhiều màn trong game. Một cú chạm vào đó có thể mở thông báo và rời game.
- Trên máy hiện banner trong danh sách cửa sổ: app chờ đến khi banner ẩn (thường vài giây). Máy khác: app chờ 6 giây sau mỗi thông báo.
- Banner (cuộc gọi đang đổ chuông, thanh thông báo bị kéo xuống) còn che sau 30 giây: tạm dừng, không chạm vào đó.
- Thông báo chạy ngầm (nhạc, tải xuống, tiến độ của app) và toast không làm app chờ.

**Tùy chọn "Im lặng thông báo khi chạy"** (màn hình chính, dưới "Hiện vùng thao tác"):
- Khi lượt chạy, app bật Không làm phiền riêng của app: cuộc gọi, người gọi lại, báo thức và âm thanh game vẫn bình thường. Các thông báo khác không kêu và không bật lên, vẫn nằm trong thanh thông báo.
- Tạm dừng, kết thúc lượt, hay app bị tắt giữa chừng (khi mở lại) đều tắt chế độ này. Cài đặt Không làm phiền của bạn không bị đổi.
- Lần đầu bật cần cấp quyền "Không làm phiền" cho AOV Collector trong Cài đặt; app mở sẵn trang đó.

## v0.13.39: tìm đúng thẻ Cờ Liên quân trong hàng chế độ giải trí

Game đã thêm chế độ mới. Thẻ Cờ Liên quân không còn ở giữa hàng (giờ là thẻ thứ 4, sau "Đấu hỗn loạn") và hàng thẻ kéo ngang được. Bản cũ bấm cố định vào giữa hàng, nên chọn nhầm "Đấu hỗn loạn", rồi có thể bấm luôn "Bắt đầu" mà không kiểm tra chế độ đang chọn.

- App tìm thẻ theo **tên "Cờ Liên quân" dưới thẻ**, ở bất kỳ vị trí nào trong hàng, rồi bấm vào ảnh thẻ ngay trên tên.
- Không thấy thẻ: chọn mục Giải trí một lần, rồi kéo hàng thẻ sang trái (tối đa 3 lần), sau đó kéo ngược lại (tối đa 3 lần).
- **"Bắt đầu" chỉ được bấm khi tiêu đề lớn góc trên bên phải là "Cờ Liên quân".** Chọn thẻ 3 lần mà tiêu đề vẫn là chế độ khác, hoặc kéo hai chiều vẫn không thấy thẻ: tạm dừng, không bấm Bắt đầu.
- Có trong **Chỉnh từng bước**: "Cờ: thẻ Cờ Liên quân" (nhận diện) và hai cú kéo hàng chế độ.

## v0.13.38: Cờ không dừng ở tướng tên dài bị đọc cụt

Lượt Cờ dừng ở Florentino: trước khi bấm giữ ảnh chiêu, app đọc tiêu đề thành "Florentin" (mất chữ cuối). App chỉ chấp nhận tên khớp từng chữ, nên coi đây không phải màn của Florentino, chờ 30 giây hai lần rồi tạm dừng.

- Giờ tiêu đề vẫn được nhận là của tướng đang mở khi chỉ mất tối đa **2 chữ cuối** và còn ít nhất 5 chữ ("Florentin", "Wonder Wom").
- Tên của tướng khác vẫn chặn như cũ: "Raz" trên màn Lumburr, "Omen" với Omega, "Floren" (mất quá nhiều), "Florentinos" (dài hơn tên).

## v0.13.37: Lấy thông tin tướng chụp đủ mô tả chiêu dài

Một số chiêu (ví dụ chiêu 2 "Gắn Ác Duyên" của Dyadia) có mô tả ở tab **Chi tiết** dài hơn khung chữ. Trước đây app chỉ chụp phần đầu.

- Sau ảnh Chi tiết, app xem dòng cuối của khung chữ. Nếu chữ chạy sát đáy khung (còn tiếp), app kéo lên trong vùng chữ, giữ tay một chút trước khi nhấc để chữ dừng đúng chỗ, không trôi quá dòng.
- Mỗi phần mới được lưu là `skill-2-detail-2`, `skill-2-detail-3`… cạnh ảnh `skill-2-detail` như cũ. Hai phần liền nhau trùng nhau một đến hai dòng để đọc nối được.
- App dừng cuộn khi một lần kéo không làm chữ đổi nữa (đã hết), và tối đa 5 lần cuộn mỗi chiêu.
- Chiêu có mô tả ngắn không bị kéo; mỗi chiêu chỉ tốn thêm một lần chụp màn hình để kiểm tra (khoảng nửa giây).
- Nếu Android từ chối cú kéo, app giữ ảnh đã chụp, ghi nhật ký và làm tiếp, không dừng lượt.
- Cú kéo có trong **Chỉnh từng bước** ("Tướng: cuộn mô tả chiêu") để chỉnh cho màn hình khác.
- Vị trí khung chữ được đo trên ảnh 2400×1080 của máy bạn. Tab Tóm tắt chưa được cuộn.

## v0.13.36: Cờ không dừng vì một cú chạm hụt, Tiếp tục chỉ đúng cách khi màn không có UID

**Cú chạm bị Android từ chối:**
- Trước đây app chỉ ghi "Không chạm được màn hình." mà không biết vì sao. Giờ mỗi cú chạm thất bại được ghi vào nhật ký lượt kèm lý do thật: rơi vào bảng nổi, Android hủy cú chạm (tay chạm màn hình, thông báo hoặc cử chỉ hệ thống chen vào), Android không nhận…
- Ở Cờ Liên Quân, khi chạm **mở thẻ quân cờ** hoặc chạm **dấu X đóng chi tiết** bị từ chối, app chờ 2 giây và xem lại màn hình:
  - Vẫn đúng danh sách và chưa mở chi tiết (hoặc chi tiết vẫn còn mở khi cần đóng): chạm lại **một lần**.
  - Cú chạm thật ra đã có tác dụng (chi tiết đã mở hoặc đã đóng): không chạm lại, làm tiếp. Nhờ vậy dấu X không bao giờ bị chạm lần nữa lên danh sách, nơi có nút sắp xếp "Giá từ thấp đến cao".
  - Lần thứ hai vẫn bị từ chối: tạm dừng với "Không chạm được màn hình sau 2 lần", chưa đánh dấu tướng đó là xong.

**Bấm Tiếp tục khi đang ở Bách khoa Cờ:**
- Màn Bách khoa không hiện UID, nên app không thể kiểm tra tài khoản ở đó và vẫn không tự thao tác gì.
- Trước đây câu báo khuyên "tạo lượt mới". Giờ, với các bước tự tìm đường từ sảnh (Cờ Liên Quân, Chiến khu, Bảng phân bậc, Đề cử Pro), câu báo hướng dẫn: bấm mũi tên quay lại của game đến sảnh chính (có UID ở cuối màn hình) rồi bấm Tiếp tục. Bước sẽ tự đi lại từ sảnh và bỏ qua phần đã lưu.
- UID khác tài khoản test vẫn bị chặn như cũ. Macro tọa độ giữ nguyên câu báo cũ.

## v0.13.35: không còn dừng vô lý ở ảnh chiêu Cờ, Đề cử Pro bấm được Dùng combo

**Cờ Liên Quân:**
- Ảnh chiêu của tướng lưu ở ảnh chi tiết 1 vẫn đúng như trước. Lỗi nằm ở **ô dò thêm** trên ảnh chi tiết sau (sau khi cuộn mô tả): app chờ 30 giây mà màn chưa đủ ổn định để bấm giữ, rồi tạm dừng với câu "ảnh có thể đã lưu", dù **chưa hề bấm giữ** nên không thể có ảnh nào được lưu.
- Giờ trường hợp "chưa bấm giữ" là một kết quả riêng:
  - Tướng đã có ảnh chiêu trong lượt: bỏ qua ô dò đó, ghi vào nhật ký, làm tiếp. Không dừng.
  - Tướng chưa có ảnh chiêu: thử lại một lần; vẫn không được thì tạm dừng với câu đúng sự thật: "chưa có ảnh nào được lưu ở bước này".
- Khi màn chi tiết đã cuộn, một dòng mô tả chiêu bị cắt (ví dụ "địch trúng chiêu sẽ chịu") không còn bị coi là tên tướng khác để chặn việc lưu. Tên tướng thật khác (như "Raz" trên màn của Lumburr) vẫn chặn như trước.
- Nhật ký ghi thêm tên đọc được ở vùng tiêu đề khi màn chưa ổn định, để dễ xem lại.

**Đề cử Pro:**
- Tên người đề cử có ký tự trang trí ("彡MinhVĩĐại彡", "càrốt€"). OCR đọc chúng khác nhau giữa các ảnh (mất ký tự, hoặc đọc thành số hay chữ), nên app không bao giờ thấy cùng một dòng hai lần: vuốt xuống 1 lần, vuốt lên 15 lần rồi dừng, chưa bấm Dùng combo.
- Giờ hai tên được coi là cùng một người khi chỉ khác ký tự trang trí ở hai đầu, hoặc lệch 1–2 chữ trong tên dài. Combo vẫn phải khớp đủ trang bị, phép bổ trợ và bảng ngọc như trước, nên không bấm nhầm combo.

## v0.13.34: tiếp tục cả lượt khi ảnh gốc còn chờ đối chiếu

Ảnh chiêu đã gửi xác nhận lưu nhưng chưa khớp biên nhận được ghi riêng là **chờ đối chiếu**, không còn chặn các tướng tiếp theo khi màn chi tiết đã xác minh an toàn. Giữ nhật ký và ứng viên ảnh trong vùng riêng; không giả là đã xác minh, không nhấn Save Photo lại, không gán ảnh theo thời gian cho tướng khác. Đối chiếu nền có ngân sách qua khởi động lại và tổng kết riêng số đã xác minh/chờ kiểm tra. Giữ nguyên sửa Đề cử Pro của v0.13.33. Xem [vòng đời và giới hạn](docs/CHESS_NATIVE_RECEIPTS.md).

## v0.13.33: đọc đủ combo Pro trước khi cuộn

Đề cử Pro đọc riêng vùng danh sách ở độ phân giải gốc phóng 2×, gồm dòng và phần tử OCR, để giữ tên người đề cử và số bảng ngọc nhỏ. Nhận vùng tên bắt đầu từ cạnh trái thực của thẻ, không loại nhầm vì tên lệch trái. Khi chưa đọc đủ thẻ, app giữ nguyên danh sách và đọc lại tối đa hai lần thay vì tiếp tục vuốt lên nhiều lần. Vẫn xác minh thứ hạng, toàn bộ combo và giữ top 1 sau thu thập; không bấm chỉ vì thấy nút. Bao gồm sửa ảnh Cờ v0.13.32. Xem [phạm vi và kiểm thử Pro](docs/PRO_BUILDS.md).

## v0.13.32: căn ảnh Pictures theo pixel khi tiếp tục lượt Cờ

Sửa khoảng trống giữa các vị trí dò ảnh 128×128: thêm tối đa 972 phép so có giới hạn theo pixel và kích thước phân số, thay vì chỉ chia vị trí theo lề còn lại của icon. Giữ nguyên ngưỡng RGB/tương quan/tỷ lệ ô khớp và các điều kiện file mới, đúng thời gian, duy nhất. Nhật ký cũ vẫn được đối chiếu khi bấm Tiếp tục, không gửi Save Photo lần nữa. Chẩn đoán mới có `fine` và báo ứng viên tốt nhất theo cả ba điều kiện. Xem [kiểm thử và giới hạn](docs/CHESS_NATIVE_RECEIPTS.md). Chưa xác nhận ảnh Hayate thực tế chỉ từ log; kiểm thử tổng hợp không thay thế thử trên máy.

## v0.13.31: căn ảnh chiêu chính xác hơn, bảng gọn thật sự

Đối chiếu ảnh chiêu thêm bước căn vị trí/kích thước mịn quanh vài ứng viên tốt nhất; giữ nguyên ngưỡng màu/cấu trúc, yêu cầu một file mới duy nhất và mốc thời gian. Không coi “có ảnh trong Pictures” là đủ, không tải lại attempt chưa rõ. Có chẩn đoán số đo so ảnh thay vì chỉ báo không khớp. Xem [bằng chứng và giới hạn](docs/CHESS_NATIVE_RECEIPTS.md).

Bảng thu gọn còn khoảng **56×56 dp**, gồm vùng chạm **48×48 dp**; thùng rác được nâng lên **80 dp trên vùng điều hướng**, có giới hạn cho màn ngang ngắn. Thả vào vẫn chỉ tạm dừng và ẩn bảng, không xóa ảnh. Xem [kiểm thử bảng nổi](docs/FLOATING_PANEL.md).

## v0.13.30: đối chiếu ảnh Cờ không dừng sớm

Đợi ảnh chiêu tải thật và popup ổn định trước chụp/nhấn giữ; kiểm lại ảnh bằng khung mới sau ghi journal, không lướt qua placeholder. Chờ Pictures có giới hạn thay vì chỉ ba lần đọc ngắn; giữ ý định Sure qua tạm dừng và không tải trùng. Báo đúng lý do thiếu biên nhận thay vì luôn báo lỗi ghi dấu. Không bỏ qua lỗi lưu trữ thật hoặc ảnh chưa khớp. Xem [kiểm thử và giới hạn](docs/CHESS_NATIVE_RECEIPTS.md).

Bảng nổi gọn bỏ Cài đặt; bảng mở rộng đưa Cài đặt (bánh răng) và Chỉnh bước (bút) lên hàng trên. Popup ảnh giữ nguyên trạng thái đầy đủ khi cập nhật nền, không xóa rồi thêm lại dòng “Ảnh gốc”; nút đóng dùng icon rõ ràng với vùng chạm lớn. Kéo bảng nổi vượt ngưỡng chạm sẽ hiện đích thùng rác phía dưới: thả vào để tạm dừng rồi ẩn bảng như nút Đóng; thả ngoài chỉ đổi vị trí, không xóa ảnh/dữ liệu. Các loại tác vụ có icon riêng bên cạnh tên, gồm fallback cho macro tùy chỉnh. Xem [hành vi bảng nổi và kiểm thử](docs/FLOATING_PANEL.md).

## v0.13.28: nhận đúng cửa sổ nhập mã mời

Luồng nhập mã nhận cả thanh nhập do bàn phím quản lý và hộp nhập native do Unity/game quản lý. Nhánh game phải có ô đang nhận tiêu điểm và nút OK cùng cửa sổ, đúng vị trí; cửa sổ bàn phím đang chọn vẫn phải được xác minh. Bảng nổi được ẩn trong các đoạn đọc/ghi ngắn và tới khi thao tác OK kết thúc, không ẩn toàn bộ lượt chờ. Giữ xóa sạch mã cũ, đọc lại mã mới và xác minh bàn phím đóng trước khi gửi trong game. Xem [phạm vi kiểm thử và giới hạn](docs/INVITATION_INPUT.md).

## v0.13.20: đọc lại phần chồng lặp Pro có kiểm chứng

Khi một khung hình chưa khớp dòng Pro trước đó, app đọc lại tối đa hai lần mà không cuộn. Nếu vẫn thiếu bằng chứng, app về đầu đã xác minh rồi đọc lại bằng bước ngắn hơn, tối đa bốn lượt; thứ hạng và chữ ký combo đã đọc được giữ nguyên để đối chiếu, không bỏ qua dòng hay đoán combo. Vùng tooltip thuộc tính không còn làm sai dấu hiệu chuyển động danh sách; về đầu phải thấy dòng đã nhận diện thực sự dịch chuyển. Giữ kiểm tra đầy đủ cuối danh sách, top 5 gốc, khôi phục top 1, và không tự bấm lại thao tác áp dụng chưa rõ kết quả. Xem [giới hạn và kiểm thử](docs/PRO_BUILDS.md).

## v0.13.19: lý do dừng thao tác Pro và sửa hiệu chỉnh

- Lỗi Pro ghi rõ thao tác, lý do chặn trước khi gửi hoặc Android từ chối/hủy sau khi gửi; không còn chỉ báo chung “Không cuộn được danh sách Pro”. Lịch sử `events.txt` giữ cấu hình màn hình, điểm mặc định/hiệu lực và hình học cửa sổ đã ẩn danh.
- Hiệu chỉnh Pro đã lưu nhưng không còn an toàn vẫn mở được **Chỉnh từng bước** để sửa hoặc **Mặc định**. Nút **Chạy bước / Tiếp** vẫn kiểm tra vùng an toàn trước khi chạm, không bỏ qua chặn cửa sổ hoặc tự thử lại.
- Hai dòng lỗi máy thật cũ chưa đủ xác định nguyên nhân thiết bị; bản này sửa lỗi không mở được chỉnh và bổ sung bằng chứng để phân biệt nguyên nhân. Xem [giới hạn hiệu chỉnh Pro](docs/PRO_BUILDS.md).

## v0.13.18: vào game chung và cài đặt nhanh

- Mọi tác vụ, gồm Pro, Cờ và macro nhập từ file, đều đi qua kiểm tra vào game/UID chung khi Chạy hoặc Tiếp tục. Chọn tài khoản đã lưu cho lượt; thiết lập bỏ kiểm tra UID của macro cũ không còn vô hiệu hóa bước kiểm tra này.
- Nếu đã ở sảnh đúng tài khoản, app bỏ qua đóng/mở lại game và chờ popup khởi động. Nếu chưa ở sảnh, app dùng luồng vào game an toàn, không tự buộc dừng game.
- Bánh răng **Cài đặt** trên bảng nổi gọn cho phép đổi nhận diện sảnh, bật điểm chạm/đường vuốt và mở quản lý tài khoản. Cách vào game/tài khoản chỉ đổi khi không còn lượt đang chạy hoặc tạm dừng; UID không có tùy chọn bỏ kiểm tra.
- Bước đang dở và macro tọa độ được giữ nguyên màn hình. Nếu không đọc được UID tại đó, app dừng an toàn thay vì về sảnh rồi bấm nhầm tọa độ: mở đúng màn hình có UID để thử lại, hoặc tạo lượt mới từ sảnh bằng tác vụ tự điều hướng.
- Chi tiết và giới hạn kiểm tra: [luồng vào game chung](docs/SHARED_GAME_ENTRY.md). Chế độ chỉnh từng bước và hiệu chỉnh đã lưu được giữ nguyên.


## v0.13.17: đối chiếu ảnh chiêu đã lưu trước khi thử lại

Lưu ảnh chiêu Cờ Liên Quân giờ giữ mốc từng thao tác, đối chiếu ảnh mới trong Pictures theo định danh, thời gian và nội dung biểu tượng, rồi ghim đúng file vào tiến độ. OCR không rõ không còn đồng nghĩa “chưa lưu ảnh”; khi Tiếp tục, app đối chiếu lần trước trước khi cân nhắc thao tác mới. Màn chi tiết không đổi được nhận diện bằng ảnh tham chiếu nếu tiêu đề bị khuất, nhưng không bỏ qua prompt hoặc tên tướng mâu thuẫn. Không bấm Sure mù, không nới an toàn cửa sổ/Stop/Pause. Xem [cơ chế, kiểm thử và giới hạn](docs/CHESS_NATIVE_RECEIPTS.md).

## v0.13.16: thu thập combo Đề cử Pro

Thêm tác vụ **Đề cử Pro · Combo tướng**: từ sảnh → Trang bị → đổi tướng, bỏ Đã nhận khi đang tick, duyệt danh sách đầy đủ. Mỗi tướng lấy tối đa năm combo thực có, chụp Chuẩn bị sau khi xác minh áp dụng, rồi giữ đúng top 1 ban đầu mà không chụp thêm. Nếu đọc rõ thông báo không có Pro, chụp ngay danh sách trống; tải lỗi hoặc OCR không rõ thì dừng, không báo trống. Có tiến độ bền vững và Chỉnh từng bước dùng cùng thao tác thật, không đăng nhập/mua tướng/Lưu ảnh trong game.

Luồng mới **cần xác nhận trên điện thoại/game thật**. Xác minh so các thành phần combo nhìn thấy và tổng cấp ngọc, không đọc thành phần ngọc ẩn. Xem [hướng dẫn, tiến độ và giới hạn](docs/PRO_BUILDS.md). Không thay đổi các cổng kiểm thử an toàn hiện có.

## v0.13.15: thao tác an toàn cạnh tay nắm thanh bên

Báo cáo trên máy thật xác định W01 do hai cửa sổ nhỏ, không có tiêu điểm và không hoạt động ở mép trái, trong khi Liên Quân vẫn phủ toàn màn hình và nhận tiêu điểm. Bản này cho phép tối đa hai tay nắm có hình học tương tự: nằm sát mép trái/phải, trong dải cạnh giới hạn theo inset màn hình, kích thước nhỏ và không hoạt động. Không dùng danh sách tên ứng dụng được bỏ qua.

Trước khi đóng bảng chỉnh và trước từng đoạn chạm/vuốt, app kiểm tra lại cửa sổ và **toàn bộ vùng đường chạm**, kể cả khoảng cách an toàn với mọi cửa sổ khác. Chạm/vuốt gần hoặc xuyên thanh bên vẫn bị chặn với W11; thanh mở rộng, có tiêu điểm, cửa sổ nổi/chia đôi, vùng không xác định và lớp trợ năng khác vẫn bị từ chối. Không tự thử lại thao tác khi chỉnh từng bước. Kiểm thử dùng cửa sổ và bộ nhận chạm giả lập, không đăng nhập game; kết quả trên máy thật vẫn cần được xác nhận.

Kiểm thử CI tách rõ hai phần: API 30 vẫn bắt buộc chạy chạm/vuốt thật, Home/đa nhiệm, chặn sai ứng dụng, hủy và toàn bộ bộ kiểm thử cũ; các mẫu cửa sổ đúng báo cáo trên máy được kiểm bằng unit test. Cửa sổ thanh bên giả lập chạy riêng trên API 34, cùng phiên bản Android của báo cáo. Nếu Android không công bố cửa sổ giả qua trợ năng, phần này báo thất bại/chưa xác minh, **không tính là đã kiểm thử thành công** và không làm bỏ qua bộ kiểm thử API 30. Kết quả mô phỏng không thay thế xác nhận trên máy thật.

## v0.13.14: lý do chặn thao tác chỉnh từng bước

Nếu **Chạy bước / Tiếp** bị chặn trước khi chạm game, bảng chỉnh hiện mã và lý do cụ thể thay cho thông báo chung. Bấm **Chép lý do lỗi** để chép báo cáo rồi tự dán khi cần hỗ trợ. Báo cáo chỉ gồm phiên bản, kích thước/vùng an toàn màn hình và loại/vị trí/trạng thái cửa sổ; không lấy ảnh màn hình, chữ trong game, tài khoản hay tên ứng dụng khác. Chỉ thao tác bấm nút chép mới ghi clipboard; báo cáo không tự gửi đi hoặc lưu vào lịch sử thu thập.

Đây là bổ sung chẩn đoán cho lỗi trên máy thật, **chưa khẳng định đã sửa nguyên nhân bị chặn trên mọi máy**. Các kiểm tra đúng game, tiêu điểm, cửa sổ nổi/chia đôi và vùng an toàn vẫn giữ nguyên. Khi khung game có viền đen hoặc thanh bên của hãng máy, hãy gửi mã hoặc báo cáo để xác định đúng điều kiện đang chặn, không bỏ qua kiểm tra an toàn.

App thử nghiệm Android 11+ để chụp dữ liệu menu Liên Quân VN bằng macro. Ảnh lưu trên máy, OCR chạy ngoại tuyến; app không có quyền Internet.

## Cách dùng
1. Bấm **Trợ năng** trên header và bật AOV Collector trong cài đặt hệ thống.
2. Bấm **Bảng nổi**, mở game thủ công và vào màn hình tướng.
3. Mở danh sách macro trên bảng nổi; tìm tên có dấu hoặc không dấu.
4. Chọn **Lấy thông tin tướng**, rồi chọn cách lấy: **Theo tên tướng** (tick các tướng đã lưu hoặc gõ tên), **Các ô đầu danh sách** (số ô cần thu thập) hoặc **Tướng đang mở**. Với hai cách đầu, tick **Tự mở game** để app tự mở Liên Quân, đóng popup sự kiện và vào danh sách tướng.
5. Bấm **Chạy**. Nút đổi thành **Tạm dừng**, rồi **Tiếp tục**. **Dừng** kết thúc lượt và giữ ảnh.
6. **Chụp** lưu ảnh thủ công. Nút **Ảnh** có icon lịch sử mở bảng tiến trình bên phải: ảnh thu nhỏ của lượt hiện tại theo thứ tự, tên như violet-1, violet-2, trạng thái đang lưu/đã lưu/lỗi và tiến độ ảnh gốc, Drive. Bấm ảnh để mở lớn, dùng Trước/Sau hoặc vuốt để đổi ảnh, +/− hoặc chụm hai ngón để phóng to. Chép ảnh để dán sang app khác. Chọn nhiều ảnh → Xuất để lưu ZIP. Kéo tiêu đề để di chuyển, × để đóng riêng. Bảng có ước lượng thời gian từ các lượt đã hoàn tất cùng tác vụ. [Chi tiết bảng tiến trình](docs/CAPTURE_PROGRESS.md). **Về app** tạm dừng và mở lịch sử. **Đóng** tạm dừng và ẩn bảng. Dấu **−** thu gọn, vẫn chạy.
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

Workflow thủ công có tùy chọn `metrics_probe=true` để ghi một thí nghiệm hữu hạn xoay ngang rồi kết nối lại dịch vụ trên API 30. Commit có nhãn `[metrics probe]` cũng chạy thêm thí nghiệm này song song, vẫn giữ các kiểm thử thông thường. Chế độ chẩn đoán chỉ so sánh cấu hình/ngữ cảnh/vùng cửa sổ, không gửi thao tác chạm và không thay thế hay tính là đã vượt các kiểm thử thông thường. Kết quả không đồng nhất được ghi là chưa giải quyết; không xoay/kết nối lại nhiều lần để ép thành công.

## Giới hạn
Screenshot cần API 30+, có thể thất bại nếu game chặn chụp. Nút Dừng hủy các bước tiếp theo; một gesture/screenshot đã gửi Android có thể vẫn hoàn thành. Tạm dừng khi bước đang chạy sẽ chờ hoàn tất bước đó rồi lưu checkpoint; riêng bước vào game và bước lấy chiêu bị ngắt ngay và làm lại từ đầu khi Tiếp tục. Nếu app bị kill giữa một bước, bước đó có thể chạy lại khi tiếp tục. Thay đổi UI/độ phân giải/tỉ lệ màn hình có thể làm tọa độ lệch; chuẩn hóa tỉ lệ không thay thế hiệu chỉnh. Một popup hệ thống hoặc rời game sẽ tạm dừng. Không có bảo đảm phát hiện mọi popup Unity.

## Sửa điều hướng Thống kê và bộ lọc Chiến khu

- **Thống kê tướng:** tìm đúng vùng chữ nhỏ **Bảng xếp hạng đầy đủ**, kể cả OCR tách chữ; bỏ qua vùng truy cập chứa cả trang có tâm nằm sai chỗ. Nếu chữ mờ, đọc riêng phần đầu trang ở kích thước lớn hơn. Sau khi bấm, chờ bảng mở và chỉ thử lại một lần khi vẫn xác định được đúng chữ. Không bấm lặp thanh bên khi thiếu chữ; lượt dừng kèm ảnh chẩn đoán nếu không mở được bảng.
- **Chiến khu:** mỗi lần mở **Chọn tướng**, kiểm tra viền ô và dấu tick **Đã nhận**. Chỉ bấm khi đang bật, rồi xác minh đã tắt trước khi về đầu lưới và đếm tướng. Ô đã tắt được giữ nguyên, kể cả khi Tiếp tục. Không đọc được ô hoặc bấm không tắt được thì dừng có lý do, tránh báo hoàn tất trên danh sách chỉ có tướng đã sở hữu.
- Kiểm thử bổ sung: vùng bấm OCR đầy đủ/tách chữ/vùng cha/sai vị trí; bộ lọc bật/tắt/mất/không nhận chạm; mở lại và tiếp tục; lưới 30 tướng với 14 tướng đã sở hữu. Nhận dạng ô được kiểm tra trên ảnh thật và các kích thước màn hình khác nhau. Ảnh báo lỗi được kiểm tra cục bộ; các ca OCR Android mới dùng giao diện giả vẽ lúc chạy, không đăng ảnh đính kèm lên kho mã và không cần mở game hay đăng nhập tài khoản.


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

## v0.4.7: tự mở game, đóng popup sự kiện và vào danh sách tướng

- **Tùy chọn mới "Tự mở game và vào Danh sách tướng"** trong tác vụ Danh sách tướng, mặc định tắt. Khi bật, bấm **Chạy** ở bất kỳ đâu (màn hình chính, trong app hay trong game). App sẽ:
  1. **Mở Liên Quân**, nếu game chưa ở phía trước.
  2. **Chờ qua logo và màn tải.** Mỗi khoảng 1,5 giây app chụp một ảnh để xem đang ở màn nào.
  3. **Đóng từng popup sự kiện bằng nút X.** App chỉ bấm khi thấy nút X ở cùng một chỗ trong 2 ảnh liên tiếp, để không bấm lúc popup còn đang hiện dần.
  4. **Bấm "Tướng" ở sảnh.** App chỉ bấm khi sảnh đã thông thoáng 2 giây, vì popup thường hiện ngay sau khi sảnh hiện ra.
  5. **Chạy tiếp như cũ** khi thấy lưới danh sách tướng: đưa danh sách về đầu, cuộn có đo và chụp từng tướng.
- **Nếu game đang mở ở một trang khác** có mũi tên "‹" của game (trang tướng, bảng chiêu…), app bấm mũi tên đó để quay về.
- **App không dùng phím Back của Android ở bước này.** Theo thử nghiệm, Back không đóng được popup quảng cáo, và bấm Back 2 lần ở sảnh sẽ thoát game.
- **Cách nhận ra từng màn** (đo trên ảnh 2400×1080 của bạn):
  - **Sảnh:** 4 biểu tượng trắng ở thanh dưới (túi, mũ, kiếm, sách) chiếm 28–64% ô của chúng. Khi popup phủ lên, chúng tối đi gần như hết; màn tải và danh sách chỉ đạt tối đa 6%. Vì vậy sảnh bị popup che không bị nhầm là sảnh trống.
  - **Nút X của popup:** tìm trong phần trên bên phải màn hình (từ 55% chiều ngang, 45% chiều cao trở lên), nên cả nút X lùi vào trong cũng được.
    - App nhận theo **hình dạng**: hai nét chéo sáng hơn nền xung quanh, nên không phụ thuộc màu hay độ sáng. Popup chụp lúc đang mờ vẫn nhận được.
    - Nút X phải **đứng riêng**: chữ X nằm trong một từ (như "XỊN", "XẠ THẦN") có nét khác sát bên nên bị bỏ qua.
    - Có nhiều chỗ giống X thì chọn chỗ gần góc trên phải nhất.
  - **Danh sách tướng:** lưới thẻ tướng như trước, và phải có thẻ thật. Màn logo toàn đen cũng có khe trống giữa các cột, nên không thể chỉ dựa vào khe.
- **App tạm dừng, báo lý do và lưu ảnh màn hình lúc đó trong lượt** (xem trong **Nhật ký lượt**) khi:
  - Bấm X 3 lần mà vẫn đúng popup đó, ảnh không đổi, hoặc đã đóng 12 popup mà vẫn còn. Các popup nối tiếp nhau thường có X ở cùng một chỗ; app nhận ra popup mới vì ảnh đã khác, nên không dừng nhầm.
  - Một màn không nhận ra đứng yên 90 giây. Màn tải vẫn thay đổi nên app chờ tiếp.
  - Sau 45 giây vẫn chưa thấy game. Một số máy chặn mở app từ nền mà không báo.
  - Sau 5 phút vẫn chưa vào được danh sách tướng.

  Bạn xử lý bằng tay rồi bấm **Tiếp tục**: app làm lại từ màn đang thấy.
- **Thời gian ước tính** cộng thêm khoảng 1 phút khi bật tùy chọn này.
- **Kiểm tra:**
  - 9 ảnh thật bạn gửi (logo, màn tải, 3 popup, 2 sảnh, danh sách) đều được nhận đúng. UID và tên người chơi đã được che trước khi đưa vào repo.
  - Nút X được vẽ thêm ở chỗ khác với 3 cỡ khác nhau: tìm đúng chỗ.
  - Chữ X có nét liền kề: bị bỏ qua. Sảnh bị làm tối như có popup: không bị nhận là sảnh.
  - Chuỗi thao tác được thử bằng kịch bản: đóng từng popup, 5 popup liên tiếp có X cùng một chỗ, chờ popup hiện sau sảnh, popup không đóng được, màn đứng yên, màn tải kéo dài, game không hiện lên.
  - Trên máy ảo Android, ảnh popup đang mờ cũng được đọc đúng.
- Cần thêm quyền "thấy" gói Liên Quân (`<queries>`), vì Android 11+ ẩn các app khác.

## v0.4.8: đăng nhập, màn quà, tài khoản test, xem lại các bước vào game

Mở rộng tùy chọn **Tự mở game** của v0.4.7 với các màn bạn gửi.

- **Màn đăng nhập** (3 nút Garena/Facebook/Google): app bấm **Đăng nhập Garena**.
  - Nhận ra nhờ 3 nút màu cam, xanh, trắng cạnh nhau, không phụ thuộc hình nền.
  - Tối đa 2 lần mỗi lần vào game.
- **Form "Đăng ký thông tin người dùng":** app bấm **Đ.KÝ SAU**, không bao giờ điền họ tên, ngày sinh hay số điện thoại.
  - **Sửa lỗi của v0.4.7:** form trắng này che đúng chỗ các biểu tượng ở thanh dưới nên bị nhận nhầm là sảnh, và app sẽ bấm "Tướng" vào ô số điện thoại. Giờ một ô chỉ được tính là có biểu tượng khi phần trắng chiếm từ 15% đến 85%, không phải trắng kín cả ô.
- **Video trailer** (tài khoản lâu không vào): app bấm nút X mảnh để bỏ qua.
- **Ba màn quà "Ấn để tiếp tục"** (thêm 2 sao, thưởng trận đầu, trang phục miễn phí): app bấm đúng vào dòng "Ấn để tiếp tục".
  - Chỉ dòng này ở phía dưới, giữa màn hình, mới tính. Chữ "Tiếp tục" ở chỗ khác, như hộp mua hàng, không được bấm.
- **Màn tổng hợp quà** ("Thưởng nhiều, gánh team dễ dàng"): bấm X, không bấm "Bắt đầu ngay".
- **Sảnh phải thông thoáng 4 giây** (trước là 2) rồi mới bấm Tướng, vì có popup hiện ra sau một lúc.
- **Màn không có ký hiệu riêng thì app đọc chữ trên đó**, bằng hai cách:
  - ML Kit có sẵn trong máy, đọc trên ảnh thu nhỏ một nửa;
  - cây trợ năng của Android, đọc được trang web và form.

### Nhiều tài khoản (v0.9.0)

- Mở **Tài khoản (N)** trên đầu app để **Thêm tài khoản**, **Sửa**, **Xóa từng tài khoản** hoặc chọn **Mặc định**. Mỗi tài khoản có tên gợi nhớ, UID, tên đăng nhập Garena và mật khẩu riêng. Khi sửa, để trống mật khẩu sẽ giữ mật khẩu cũ nếu còn đọc được.
- Trong tác vụ, bật **Kiểm tra UID · chọn tài khoản khi bấm Chạy**. Tùy chọn này tự bật **Tự mở game**; thiếu tài khoản vẫn cho thiết lập và có nút **Quản lý tài khoản** hướng dẫn thêm tài khoản, thay vì khóa ô chọn.
- Khi bắt đầu lượt mới có kiểm tra UID, app hiện **Chọn tài khoản cho lượt chạy**. Tài khoản đó được gắn với lượt và dùng cho cả UID cần kiểm tra lẫn thông tin đăng nhập Garena. Đổi tài khoản mặc định không đổi tài khoản của lượt đang dở. Tiếp tục lượt dùng đúng tài khoản đã chọn; nếu tài khoản bị xóa hoặc UID bị sửa thì app dừng và hướng dẫn sửa/tạo lượt mới, không tự chuyển sang tài khoản khác.
- Tài khoản đơn từ phiên bản cũ tự chuyển sang danh sách mới, giữ nguyên dữ liệu mã hóa. Nếu dữ liệu còn UID nhưng khóa/mật khẩu không đọc được, tài khoản vẫn hiện với trạng thái **cần nhập lại mật khẩu** để sửa. Không thể khôi phục thông tin đã bị xóa khỏi dữ liệu ứng dụng. Lượt cũ đã qua bước vào game nhưng chưa gắn tài khoản cần tạo lượt mới để xác minh đúng tài khoản; ảnh cũ được giữ.
- Tên đăng nhập và mật khẩu tiếp tục được mã hóa AES-GCM bằng Android Keystore, không ghi vào macro, nhật ký hoặc file ZIP. Lựa chọn tài khoản của lượt chỉ nằm trong dữ liệu riêng của app. App không có quyền Internet.

### Kiểm tra UID trong game

- **Mục Tài khoản trên header của app:** lưu các tài khoản và chọn tài khoản muốn dùng khi chạy.
  - Mật khẩu (và tên tài khoản) được mã hóa AES-GCM bằng khóa nằm trong Android Keystore của máy, không lấy ra được.
  - Mật khẩu không nằm trong file ZIP, không ghi vào nhật ký, và không hiện lại (chỉ Lưu lại hoặc Xóa).
  - App vẫn không có quyền Internet.
- **Tùy chọn "Kiểm tra UID · chọn tài khoản khi bấm Chạy"** trong các tác vụ có Tự mở game: Danh sách tướng, Thống kê tướng, Chiến khu và Cờ Liên Quân.
  - **Đọc UID:**
    - Ở sảnh, app đọc dòng "UID: …" mờ ở cuối màn hình. Dòng này được cắt riêng, chuyển chữ đen nền trắng và phóng to 3 lần cho dễ đọc.
    - Đọc không được 3 lần thì app mở **hồ sơ** (avatar) → **UID** → đọc số trong hộp "Xem UID" → bấm X → quay lại sảnh.
  - **Khớp:** vào danh sách tướng như thường.
  - **Khác:** app vào cài đặt (bánh răng), bấm **Đăng xuất**, rồi bấm **Xác nhận** hoặc **Đồng ý**. Ở màn đăng nhập, app bấm Đăng nhập Garena.
  - **Trên trang Garena:** app điền tài khoản và mật khẩu đã lưu rồi bấm **Đăng Nhập Ngay**, qua cây trợ năng (`ACTION_SET_TEXT`). Sau đó app kiểm tra UID lần nữa.
- **An toàn khi điền form:**
  - Chỉ điền khi trang có ô mật khẩu thật (dạng dấu chấm) và có "Quên mật khẩu?" hoặc "Tạo tài khoản mới". Nhờ vậy tài khoản không bao giờ bị gõ vào chỗ khác, như ô chat.
  - Chỉ điền khi app vừa bấm Đăng nhập Garena trong lần vào game này.
  - Mỗi lần vào game chỉ đăng xuất và đăng nhập một lần. Nếu game tự vào lại tài khoản cũ, hoặc UID vẫn khác, app tạm dừng.
  - Không có tài khoản test mà game mở trang Garena: app tạm dừng để bạn đăng nhập.
  - Gặp captcha, mã xác minh hay báo lỗi đăng nhập: trang đăng nhập vẫn còn sau 15 giây nên app tạm dừng, không thử lại.
- **Chưa có ảnh thật cho nút Đăng xuất và hộp xác nhận.** App tìm theo chữ ("Đăng xuất", "Xác nhận", "Đồng ý", thẻ "Tài khoản" hoặc "Khác"). Nếu không tìm thấy, app tạm dừng; xem lại các bước để biết nó dừng ở đâu.

### Xem lại các bước vào game

- **Tùy chọn "Lưu ảnh từng bước vào game"**, mặc định bật.
  - Mỗi ảnh app chụp để quyết định được thu nhỏ còn 1200×540 (JPEG, khoảng 100 KB), lưu kèm điều app nhận ra và việc đã làm.
  - Ảnh nằm trong thư mục `buoc-vao-game` của lượt, kèm `steps.jsonl`. Màn chờ chỉ lưu khi màn hình đổi.
- **Trang Garena chỉ lưu dòng mô tả, không lưu ảnh**, vì ảnh sẽ có tên tài khoản vừa điền.
- **Xem lại:** menu ⋮ của lượt → **Xem lại các bước vào game**. Từng bước hiện giờ, việc đã làm và ảnh, với **vòng tròn đỏ ở chỗ app đã bấm**.
- **Gửi khi có lỗi:** thư mục bước nằm trong lượt nên có trong Lưu ZIP. Nhập ZIP cũng nhận ảnh `.jpg` của thư mục này.

### Kiểm tra

- 20 ảnh thật (UID và tên người chơi đã che) được nhận đúng loại màn và vị trí nút X: trailer, 3 màn quà, tổng hợp quà, sảnh tài khoản mới, màn đăng nhập, hồ sơ, hộp Xem UID, trang Garena.
- Form đăng ký không còn bị nhận là sảnh.
- Chuỗi thao tác được thử bằng kịch bản:
  - tài khoản lâu không vào (đăng nhập → form đăng ký → trailer → 3 màn quà → tổng hợp → sảnh → danh sách);
  - UID khớp;
  - đổi sang tài khoản test;
  - đọc UID trong hồ sơ;
  - game tự vào lại tài khoản cũ;
  - trang Garena khi không có tài khoản test.
- **Trên máy ảo Android:**
  - ML Kit đọc ra "Ấn để tiếp tục" và "Đ.KÝ SAU" trên ảnh thật, kèm đúng chỗ bấm, và nhận ra trang Garena.
  - Dòng UID mờ được đọc đúng.
  - Tài khoản lưu và đọc lại được, mật khẩu không nằm ở dạng chữ thường.
- **Chưa thử được trên máy thật:** điền trang Garena thật, và đường đăng xuất trong cài đặt.

## v0.4.9: đủ 5 ô chiêu và các dạng đổi chiêu (nút ↻)

Trước đây app bấm 4 vị trí cố định trên hàng chiêu. Với Edras (5 ô) app chụp trùng chiêu 1 và bỏ sót chiêu 2, 3. Các chiêu ở dạng khác (Qi, Volkath, Liliana…) thì không được chụp.

Giờ app **đọc hàng chiêu trên ảnh chụp ngay lúc chạy**, rồi mới bấm.

- **4 hay 5 ô:**
  - App đo viền tròn của từng ô theo hai cách xếp (4 ô, hoặc 5 ô sát nhau).
  - Cách xếp đúng có viền rõ ở mọi ô (từ 73% trở lên), cách sai thì không (tối đa 65%).
  - Tên ảnh: `passive`, `skill-1` … `skill-4`.
- **Nút đổi dạng ↻:** nhận theo hình hai mũi tên (so khớp mẫu), không theo độ sáng, nên nền sáng như của Tee hay Sinestrea không bị nhận nhầm.
- **Khi có nút ↻:**
  1. Chụp xong dạng đầu, app đóng bảng chiêu rồi bấm ↻.
  2. App so từng ô với các dạng đã thấy và **chỉ chụp ô đã đổi**: Qi chỉ chiêu 3, Volkath chiêu 2, Liliana chiêu 1–3, Edras cả 5 ô.
  3. App bấm ↻ tiếp cho tới khi hàng chiêu về lại dạng đầu (tối đa 4 dạng).
  - Ảnh dạng sau có tên `form-2-skill-3-detail`…, kèm ảnh hàng chiêu của dạng đó: `form-2-icons`.
  - Nếu bấm ↻ mà hàng chiêu không đổi, app chờ thêm 1 giây, bấm lại một lần, rồi bỏ qua và ghi vào nhật ký lượt.
- **Thứ tự ảnh:** ảnh chiêu có thêm số thứ tự sau số bước, như `0012-03-hero-001-skill-2-detail`, để thư mục tướng hiện đúng thứ tự chụp.
- **Trình xem ảnh:** hiện "Chiêu 4" và "Dạng 2 · Chiêu 3 · Chi tiết".
- **Không nhận ra hàng chiêu** (bảng còn mở, màn khác): app bấm chỗ trống một lần rồi đọc lại. Vẫn không được thì app chụp theo 4 vị trí như trước và giữ ảnh `skill-row-unknown` để xem lại.
- **Các tùy chọn cũ vẫn dùng được:** Chỉ Chi tiết, Chuyển thẳng chiêu (trong mỗi dạng), Chạy nhanh.
- **Nhật ký lượt (`events.txt`)** ghi số ô và các dạng của từng tướng, ví dụ: "5 ô chiêu · 2 dạng (dạng 2 đổi passive, skill-1, …)".

### Kiểm tra

- **8 ảnh bạn gửi** (Qi, Volkath, Liliana, Edras, mỗi tướng 2 dạng):
  - đúng số ô;
  - đúng nút ↻;
  - đúng các ô đổi giữa hai dạng.
- **Trên 129 tướng của lượt 12/10:**
  - chỉ Edras có 5 ô;
  - đúng 20 tướng có nút ↻;
  - không có tướng nào bị nhận nhầm.
- **Luồng bấm được thử bằng kịch bản:**
  - tướng 4 ô không đổi dạng;
  - Qi 2 dạng;
  - Edras 5 ô đổi hết;
  - tướng 3 dạng;
  - nút ↻ lỡ một lần;
  - nút ↻ không phản hồi;
  - bảng chiêu còn mở.
- **Phần trích xuất chữ** (`extraction/merge.py` ở nhánh `claude/vlm-text-extraction`) chưa biết chiêu 4 và ảnh dạng 2: cần cập nhật riêng.

## v0.4.10: Tạm dừng và Dừng ngắt ngay bước vào game

**Lỗi:** bấm Tạm dừng hoặc Dừng khi app đang tự vào game thì bảng nổi vẫn hiện tiến trình vào game, và app vẫn tiếp tục bấm trong game.
- **Nguyên nhân:** hai nút này chỉ đánh dấu "nghỉ sau thao tác hiện tại". Bước vào game là một thao tác dài tới 8 phút nên vẫn chạy tiếp tới khi xong, và vẫn ghi tiến trình lên bảng.

**Sửa:**
- **Bước vào game và bước lấy chiêu bị ngắt ngay** khi bấm Tạm dừng, Dừng, Về app hoặc Đóng.
  - Một cú chạm đã gửi cho Android thì vẫn tới game; sau đó app không chạm, không chụp nữa.
  - Bảng nổi giữ dòng "Đã tạm dừng" thay vì tiến trình vào game.
  - Nhật ký lượt (`events.txt`) ghi "Ngắt bước … giữa chừng".
- **Khi bấm Tiếp tục, bước bị ngắt làm lại từ đầu:**
  - Vào game: app nhìn màn hình hiện tại rồi đi tiếp.
  - Lấy chiêu: ảnh trùng tên được ghi đè. Nếu lúc ngắt hàng chiêu đang ở dạng 2, app bấm ↻ cho về dạng đầu trước khi chụp lại, nên ảnh dạng 2 không bị lưu nhầm thành dạng đầu.
- **Các thao tác ngắn khác** (chạm, chờ, chụp, chọn tướng trong danh sách) vẫn chạy nốt rồi nghỉ, như trước. Chọn tướng chạy nốt để không mất vị trí đã đo của danh sách.

### Kiểm tra

- Kịch bản cho bước lấy chiêu:
  - bị ngắt sau ảnh thứ hai thì không chạm và không chụp thêm;
  - làm lại khi Qi đang ở dạng 2 thì bấm ↻ trước, rồi chụp đủ như lần đầu.
- 56 test đều qua.

## v0.4.11: đóng hẳn game còn chạy trước khi vào

Game còn trong đa nhiệm sẽ quay lại đúng chỗ đã bỏ dở, có thể là một màn app chưa biết. Vì vậy trước khi mở, **bước Tự mở game giờ đóng hẳn Liên Quân nếu còn chạy**, để game luôn khởi động lại từ đầu (logo, đăng nhập, popup).

- **Cách đóng:** Android không cho app này tắt tiến trình của app khác (từ Android 14 chặn hẳn), nên app đi qua Cài đặt bằng Trợ năng:
  1. Mở **Cài đặt → Thông tin ứng dụng của Liên Quân**.
  2. Bấm **Buộc dừng**.
  3. Bấm **OK** (hoặc **Buộc dừng**) trong hộp hỏi của Android.
  4. Mở game.
  - Nút Buộc dừng bị mờ nghĩa là game không chạy: app mở game luôn.
- **An toàn:**
  - Chỉ bấm trên trang có tên Liên Quân.
  - Chỉ bấm nút có đúng nhãn "Buộc dừng" / "Force stop" (khớp cả chữ, không khớp một phần). Không bao giờ bấm Gỡ cài đặt hay Tắt.
  - Chỉ trả lời hộp hỏi có nhắc tới Buộc dừng và có nút Hủy.
  - Trang Cài đặt tự đóng khi game mở lên trên nó, không nằm lại trong đa nhiệm.
- **Không thấy trang hoặc nút** (máy có giao diện Cài đặt lạ): app ghi lại rồi mở game như cũ, không dừng lượt.
- **Kết quả được ghi** vào nhật ký lượt và vào "Xem lại các bước vào game", dạng dòng chữ không ảnh, ví dụ "Đã buộc dừng Liên Quân để mở lại từ đầu".
- Bấm Tiếp tục sau khi Tạm dừng giữa bước vào game cũng đóng và mở lại game từ đầu.

### Kiểm tra

- **Kịch bản:**
  - trang kiểu Android gốc (OK) và kiểu Samsung (nút "Buộc dừng" trong hộp hỏi);
  - tiếng Anh;
  - game không chạy;
  - máy không hỏi;
  - trang của app khác (không bấm gì);
  - không có nút;
  - hộp hỏi khác (không trả lời);
  - bị Tạm dừng giữa chừng.
- **Trên máy ảo CI:** app Buộc dừng thật một app có sẵn qua trang Cài đặt của Android 11. Sau đó tiến trình của app đó không còn, và lần đóng thứ hai nhận ra là app không chạy.
- **Chưa thử trên máy thật của bạn:** nhãn nút trong Cài đặt của hãng điện thoại có thể khác. Nếu app ghi "Không thấy nút Buộc dừng", gửi tôi ảnh trang Thông tin ứng dụng của Liên Quân.

## v0.5.0: Thống kê tướng (Bảng Phân Bậc Tướng)

**Tác vụ mới trong danh sách macro của bảng nổi: "Thống kê tướng (bảng xếp hạng)".** Tác vụ chụp hết bảng thống kê tướng của game.

**Đường đi** (từ ảnh bạn gửi):
1. Ở sảnh, bấm nút bên trái **Túi đồ**. Biểu tượng của nút này đổi theo mùa, nên app bấm theo vị trí.
2. Trong "Tính năng khác", bấm **Cộng đồng**.
3. Trên thanh bên trái, chọn **mục thứ 2** (biểu tượng mũ tướng).
4. Bấm **Bảng xếp hạng đầy đủ**.

App bắt đầu được từ bất kỳ màn nào trên đường đi này: sảnh, Tính năng khác, Cộng đồng, hoặc chính trang Bảng Phân Bậc Tướng.

**Chụp:** 2 bảng × 3 chế độ = 6 danh sách, mỗi danh sách chụp từ đầu tới cuối.
- **Hai bảng**, đổi bằng nút **Đổi bảng xếp hạng**:
  - **Tỷ lệ:** Tier, tỷ lệ thắng, pick, ban, và các tướng khắc chế.
  - **Chỉ số:** sát thương mỗi trận, hạ, vàng, sát thương phải chịu, hỗ trợ.
- **Ba chế độ** trong ô chọn: Đấu hạng (Tất cả), Đấu 5v5, Đấu hạng (Tinh Anh trở lên).
- **Cuộn:** mỗi lần kéo chậm 0,4 màn hình (dừng tay trước khi nhấc để trang không trôi). Ảnh liền nhau trùng 1–2 dòng, nên không sót tướng.
  - Khi một lần kéo không làm bảng thay đổi, là đã hết danh sách.
  - Sau đó app vuốt nhanh lên đầu trang để đổi bảng hoặc chế độ.
- **Ảnh xếp theo thư mục**, ví dụ `ty-le-dau-hang-tat-ca/0003-ty-le-dau-hang-tat-ca-01.png`, `chi-so-dau-5v5/…`. Trong app, thư mục hiện tên như "Tỷ lệ thắng, pick, ban · Đấu 5v5", còn ảnh là "Trang 1, 2…".

**Cách nhận màn hình:**
- Các trang sau sảnh là trang web, có lề đổi theo phía của thanh điều hướng, nên app tìm theo chữ chứ không theo tọa độ cố định:
  - đọc bằng ML Kit;
  - đọc cả cây Trợ năng của trang web, nếu có.
- Sảnh được nhận bằng các biểu tượng ở thanh dưới, như trước. Đã kiểm tra trên 2 ảnh sảnh bạn gửi, kể cả bản thử nghiệm có biểu tượng khác.
- Đổi bảng hay chế độ xong, app đọc lại tiêu đề cột và ô chọn để chắc đã đổi đúng.

**Tùy chọn Tự mở game:** app đóng hẳn game còn chạy, vào game như bước Tự mở game của Danh sách tướng, nhưng dừng ở sảnh thay vì bấm Tướng. Có thể kèm kiểm tra UID và lưu ảnh từng bước như Danh sách tướng.

**Tạm dừng:** bước thống kê bị ngắt ngay. Khi bấm Tiếp tục, app tìm lại đường tới bảng và bỏ qua các danh sách đã chụp xong trong lượt.

### Kiểm tra

- **Kịch bản:**
  - từ sảnh tới bảng, chụp đủ 6 danh sách đúng thứ tự, chỉ đổi bảng 1 lần;
  - bắt đầu ở bảng Chỉ số · Đấu 5v5 và bỏ qua phần đã chụp;
  - tiêu đề cột đứng yên khi cuộn;
  - màn hình lạ;
  - bị Tạm dừng giữa chừng.
- **Ảnh thật:**
  - nhận ra sảnh;
  - phân biệt được bảng Tỷ lệ với bảng Chỉ số;
  - nhận ra bảng đã cuộn đi một dòng.
- **Trên máy ảo CI:** ML Kit đọc 6 ảnh của bạn (Tính năng khác, Cộng đồng, mục tướng, 2 bảng, ô chọn đang mở) và nhận đúng từng trang, vị trí nút, bảng, chế độ và các dòng trong ô chọn.
- **Chưa thử trên máy thật.** Ở lần chạy đầu, xin bạn xem `events.txt` của lượt và gửi tôi ảnh nếu app dừng ở trang nào.

## v0.5.1: đóng popup giải đấu live (nút X trong vòng tròn)

Sau các popup quảng cáo, game có thể hiện thêm **popup giải đấu đang phát trực tiếp** (ví dụ Swiss Stage – Tứ Phương Đại Chiến). Nút đóng của popup này là **dấu X đậm nằm trong một vòng tròn trắng**, ở góc trên bên phải (khoảng 83,5%, 7,7% màn hình). Bản trước chỉ nhận dấu X mảnh đứng riêng: vòng tròn quanh X bị coi là "có thứ khác dính vào" nên bỏ qua, và app đứng chờ ở màn này.

- **Thêm bộ nhận "X trong vòng tròn":** một vòng tròn sáng, bên trong có 2 nét chéo sáng, giữa các nét tối, ngay ngoài vòng tròn phần lớn tối. Bán kính thử từ 21 đến 39 px (trên màn cao 1080).
- **Không bao giờ bấm "XEM NGAY"**, chỉ bấm nút X.
- Video trong popup thay đổi liên tục, nên nếu bấm X mà popup không đóng, app dừng sau tối đa 12 lần bấm, như với các popup khác.

### Kiểm tra

- Ảnh bạn gửi (UID đã che) được nhận là popup, nút X đúng chỗ.
- 19 màn vào game cũ vẫn ra đúng như trước.
- Trên 176 ảnh khác (129 trang tướng, ảnh chiêu, sảnh, Cộng đồng, bảng xếp hạng, màn đăng nhập…), chỉ các popup thật có nút X: không nhận nhầm.

## v0.5.2: thống kê không dừng sớm, chọn bảng, thời gian ước tính theo lượt trước, nút Dừng

### Hai danh sách thống kê bị dừng sớm

Trong ZIP bạn gửi, "Chỉ số · Đấu 5v5" dừng sau 4 trang và "Chỉ số · Đấu hạng (Tất cả)" dừng sau 5 trang, dù bảng còn nhiều tướng.
- **Nguyên nhân:** app coi "kéo mà bảng không đổi" là hết danh sách. Trên máy bạn, có lần kéo bảng chưa đổi kịp (trang đang tải thêm dòng, hoặc ảnh chụp ra trước khi trang cuộn xong), nên app kết thúc sớm.
- **Sửa:** khi một lần kéo không làm bảng đổi, app thử lại tới 3 lần:
  1. chờ 1,5 giây rồi nhìn lại;
  2. kéo lại;
  3. kéo thêm lần nữa.
  
  Vẫn không đổi mới coi là hết danh sách. Mỗi lần thử được ghi vào `events.txt`. Ở cuối thật của danh sách, việc này tốn thêm khoảng 6 giây.

### Chọn bảng muốn chụp

Form **Thống kê tướng** giờ có ô chọn:
- **Bảng:** Tỷ lệ thắng/pick/ban, Chỉ số mỗi trận.
- **Chế độ:** Đấu hạng (Tất cả), Đấu 5v5, Đấu hạng (Tinh Anh trở lên).

App chụp các tổ hợp đã tick, ví dụ chỉ Chỉ số × Đấu 5v5. Lựa chọn được nhớ cho lần sau. Mô tả tác vụ hiện "n/6 bảng".

### Thời gian còn lại theo các lượt chạy trọn trước

- **Lượt chạy từ đầu tới cuối không tạm dừng** (không bấm Tạm dừng, không lỗi, không bị tắt app) được ghi thời gian:
  - số giây mỗi tướng (Danh sách, theo các tùy chọn Chạy nhanh / Chỉ Chi tiết / Chuyển thẳng chiêu);
  - số giây một lượt Một tướng;
  - số giây mỗi bảng Thống kê;
  - thời gian vào game, ghi riêng.
- Mỗi lượt trọn mới kéo con số đã lưu về một nửa phía nó, để một lượt bất thường không quyết định hết.
- **Ước tính trong form chọn tác vụ** dùng các thời gian này khi có, kèm dòng "(theo lượt chạy trọn trước)".
- **"còn ~…" trên bảng nổi:**
  - Lúc đầu theo các lượt trước.
  - Sau mỗi tướng, nghiêng dần về tốc độ của chính lượt đang chạy, và từ tướng thứ 10 trở đi chỉ theo lượt đang chạy.
  - Thống kê tướng cũng hiện "còn ~…" khi đang chụp.

### Nút Dừng

Trước đây nút **Dừng** bấm được cả khi chưa chạy: nó đánh dấu lượt "đã kết thúc" dù chưa có gì, rồi bắt bấm Tạo lượt mới.
- Giờ nút **mờ và không bấm được** khi chưa có lượt nào, hoặc lượt đã xong.
- Nút chỉ bật khi lượt đang chạy, hoặc đang tạm dừng giữa chừng.

### Kiểm tra

- Kịch bản bảng bị kẹt 2 lần liên tiếp vẫn chụp đủ cả danh sách (6/6 trang). Trước đây sẽ dừng ở trang 3.
- Chỉ chụp các bảng đã chọn.
- Cách tính thời gian còn lại: theo lượt trước, pha dần, rồi theo lượt đang chạy.
- Trên máy ảo: nút Dừng tắt khi chưa có lượt; macro thống kê giữ đúng các bảng đã chọn, macro cũ vẫn lấy đủ 6 bảng.

## v0.6.0: Chiến khu – bảng xếp hạng người chơi theo tướng

**Tác vụ mới trong danh sách macro: "Chiến khu (BXH người chơi theo tướng)".**

### Đường đi

1. Ở sảnh, bấm **Đấu hạng**, rồi bấm **Chiến khu** ở cột trái.
2. **Lần đầu vào một mùa mới**, game hiện phần xem lại mùa. App bấm đúng các chữ trên từng màn:
   - "Ấn mở l.sử mùa";
   - "Bước kế" (2 màn);
   - "Mở mùa mới";
   - "Ấn màn hình để tiếp tục".
   
   Khi đang chiếu trailer (không có chữ), app chờ.
3. **Tài khoản chưa có khu vực** sẽ gặp hộp "Vào chiến khu". App chọn từng ô:
   - **Việt Nam → Miền Trung → Đắk Lắk.** Danh sách tỉnh dài, nên app cuộn tối đa 3 lần để tìm Đắk Lắk. Không thấy thì app chọn tỉnh đầu tiên (như bạn nói, chọn tỉnh nào cũng được).
   - Rồi bấm **Xác nhận**. Không bao giờ bấm "Chưa chỉnh".

App bắt đầu được từ bất kỳ màn nào trên đường đi này.

### Mỗi tướng

1. App bấm nút **↻ dưới thẻ tướng** để mở "Chọn tướng". Tướng được lấy theo thứ tự trong lưới, **bắt đầu từ tướng ở vị trí đầu**.
   - Mỗi lần mở, app vuốt lưới lên đầu.
   - Rồi kéo chậm xuống, mỗi lần 2 hàng. Sau mỗi lần kéo, app đo lại vị trí các hàng thẻ trên ảnh, để bấm đúng ô.
   - Hết thẻ ở ô kế tiếp là hết danh sách tướng.
2. **Tên tướng** được đọc trên thẻ (ví dụ "Flowborn", "Valhein") và dùng làm tên thư mục.
3. App chụp các bảng đã chọn. **Mặc định, theo yêu cầu của bạn:**
   - **Việt Nam · top 3:** màn đầu của tab Việt Nam, đã hiện 4 người đầu và hạng của bạn.
   - **Số 1 máy chủ:** mở trang này (nút dưới cùng bên trái), chụp người mạnh nhất mỗi miền của tướng, rồi quay lại. Nút "Đổi tướng" trên trang đó không bao giờ bị bấm. Nếu trang này hiện tướng khác tướng đang chọn, app ghi lại trong `events.txt`.
   
   **Tùy chọn, mặc định tắt:**
   - **Toàn máy chủ · ít nhất top 10:** chụp 4 màn, kéo danh sách 3 lần. Màn đầu có 4 người, mỗi lần kéo thêm hơn 2 người, nên 4 màn được khoảng 11–12 người.
   - **Tỉnh · top 3**, **Miền · top 3**.
   - **Kéo hết danh sách** các bảng đã chọn.

### Ảnh và tùy chọn

- **Tên ảnh:** `0003-hero-001-tinh-01.png`, `…-may-chu-01`, `…-so-1-may-chu-01`, nằm trong thư mục của từng tướng. Trong app, ảnh hiện "Tỉnh · trang 1"…
- **Tùy chọn:**
  - số tướng (2, 11, hoặc tất cả);
  - các bảng muốn chụp (như trên);
  - Tự mở game (dừng ở sảnh), Kiểm tra UID, Lưu ảnh từng bước.
- **Thời gian ước tính** theo các lượt chạy trọn trước, như các tác vụ khác.

### An toàn

**Nút "Đấu hạng" trong trang Đấu hạng bắt đầu tìm trận, và không bao giờ bị bấm.** Nút Đấu hạng ở sảnh nằm gần đúng chỗ đó, nên app chỉ bấm khi chắc chắn đang ở sảnh:
- các biểu tượng thanh dưới của sảnh có mặt;
- đọc được chữ "Túi đồ" / "Trang bị" ở dưới cùng;
- không có chữ nào của trang Đấu hạng.

### Tạm dừng

Tạm dừng ngắt ngay. Khi Tiếp tục, app tìm lại đường vào Chiến khu và làm tiếp từ tướng chưa xong.

### Kiểm tra

- **Kịch bản đi trọn đường:** từ sảnh, qua các màn mùa mới và hộp chọn khu vực (tìm Đắk Lắk sau khi cuộn), tới 10 tướng × 5 bảng.
  - Mặc định chỉ chụp Việt Nam (1 màn) và Số 1 máy chủ.
  - Toàn máy chủ top 10 là 4 màn.
  - Không bao giờ chạm vào nút Đấu hạng của trang Đấu hạng, "Chưa chỉnh" hay "Đổi tướng".
- **Lưới tướng:**
  - Lưới 30 tướng bị để giữa chừng từ trước: chọn đúng 30 tướng theo thứ tự.
  - Dừng đúng số tướng yêu cầu, và làm tiếp đúng từ tướng chưa xong.
  - Kéo hết danh sách.
  - Bị Tạm dừng giữa chừng.
- **Trên ảnh thật:**
  - đo đúng các hàng thẻ trong Chọn tướng (cả khi đã cuộn);
  - trang Đấu hạng không bao giờ bị nhận là sảnh;
  - nhận ra danh sách đã cuộn.
- **Trên máy ảo CI:** ML Kit đọc các ảnh bạn gửi (tên và ảnh người chơi đã làm mờ, UID đã che) và nhận đúng từng màn: Đấu hạng, 7 màn mùa mới, 4 bước chọn khu vực, Chiến khu (đọc ra tên Flowborn, Valhein), Chọn tướng, Số 1 máy chủ, trailer.
- **Chưa chạy trên máy thật.** Lần đầu nên thử 2 tướng, rồi xem `events.txt` và ảnh của lượt.


## v0.7.0: Lấy thông tin tướng – tìm tướng theo tên

**"Một tướng đang mở" và "Danh sách tướng" được gộp thành một tác vụ: "Lấy thông tin tướng".** Trong form có 3 cách lấy:

1. **Theo tên tướng** (mới):
   - Gõ để tìm trong các tên đã lưu từ những lượt trước, rồi tick các tướng cần lấy (chọn được nhiều tướng).
   - Tên chưa có thì gõ đủ tên rồi bấm **Thêm tướng: …**.
   - Không tick tướng nào thì app lấy đúng tên đang gõ.
   - Các tướng đã tick được nhớ cho lần sau.
2. **Các ô đầu danh sách:** như "Danh sách tướng" cũ (2 ô, 11 ô hoặc tất cả).
3. **Tướng đang mở:** như "Một tướng" cũ. Tên tướng không bắt buộc.

Các tùy chọn chạy nhanh, chỉ chụp Chi tiết, chuyển thẳng giữa các chiêu vẫn như cũ. Tự mở game, Kiểm tra UID và Lưu ảnh từng bước chỉ dùng cho hai cách bắt đầu từ danh sách.

### Tên đã lưu và vị trí tướng

- **Mỗi lượt lấy cả danh sách** lưu tên tướng (đọc trên trang tướng) cùng ô của nó trong danh sách. Thứ tự là thứ tự đang chọn trong game, mặc định **"Theo ngày"**: tướng mới nhất ở đầu.
- **Tướng chụp ở các tác vụ khác** (tướng đang mở, Chiến khu) được lưu tên, chưa có vị trí.
- **Lần đầu mở form,** app dựng danh sách này từ các lượt đã lưu trong máy. Sau đó app tự cập nhật sau mỗi lượt.
- Danh sách nằm trong `hero-index.tsv` của app, không nằm trong file ZIP.

### Cách tìm một tướng

1. **Đã biết vị trí:** app cuộn tới ô đó (cuộn có đo như lấy danh sách) và **đọc lại tên trên thẻ** trước khi bấm.
   - Nếu tướng nằm ở ô khác trên cùng màn (ví dụ game vừa thêm tướng mới nên các tướng lùi 1 ô), app bấm vào đúng thẻ có tên đó.
   - Nếu tên không có trên màn, app **dò tiếp**: cuộn xuống mỗi lần 2 hàng tới cuối danh sách, rồi quay lên các hàng phía trên.
2. **Chưa biết vị trí:** app dò từ đầu danh sách.
3. **Tên trên thẻ được đọc bằng ML Kit.** Tên dài từ 5 chữ cái cho phép sai 1 chữ.
   - Có 2 thẻ cùng tên (ví dụ 2 thẻ Flowborn): app chọn thẻ gần vị trí đã lưu.
4. **Tướng không có trong danh sách** thì bị bỏ qua. App ghi lại trong `events.txt` và làm tiếp tướng sau.
5. Mọi tên đọc được trên đường dò đều được lưu vị trí mới, nên lần sau app tới thẳng.

**Ảnh của từng tướng** nằm trong thư mục mang tên tướng: tên đọc trên trang tướng nếu khớp với tên đã chọn, nếu không thì dùng tên đã chọn.

### Kiểm tra

- **Danh sách giả 61 tướng** (cuộn trượt ngẫu nhiên như game), tìm "Bolt Baron":
  - đúng vị trí cũ;
  - lệch 1 ô;
  - lệch xuống 1 hàng, ra khỏi màn cũ;
  - lệch lên phía trên (dò tới cuối rồi quay lên);
  - chưa biết vị trí, gõ sai 1 chữ ("Bolt Barom");
  - 2 thẻ Flowborn;
  - tên không có trong danh sách: chạy tới cuối rồi bỏ qua.
- **Tên đã lưu:** đọc, ghi, thay tên ở một ô, bỏ các dòng không phải tên.
- **Trên máy ảo CI:**
  - ML Kit đọc tên trên ảnh danh sách bạn gửi (UID đã che): đúng ô của Flowborn, Tamyn, Dyadia, Heino, Biron, Bolt Baron, Billow, Charlotte, và đúng chỗ bấm cho Bolt Baron.
  - Form mới: tìm "bolt" rồi tick Bolt Baron (vị trí đã lưu là ô 8), thêm tên mới "Qi", ra macro 2 tướng theo tên. Cách "Tướng đang mở" vẫn nhận tên gõ vào.
- **Chưa chạy trên máy thật.** Lần đầu nên thử 2 tướng, rồi xem `events.txt`.

## v0.8.0: giao diện Tối và chỉnh giao diện

### Giao diện Sáng, Tối hoặc Theo hệ thống

- **Nút "Giao diện" mới** trên đầu màn hình chính (biểu tượng hình tròn nửa sáng nửa tối) có 3 lựa chọn:
  - **Theo hệ thống** (mặc định): theo chế độ sáng/tối của điện thoại;
  - **Sáng**;
  - **Tối**.
- Lựa chọn áp dụng cho:
  - màn Lịch sử, xem ảnh và xem lại các bước vào game;
  - các hộp thoại trong app;
  - danh sách tác vụ và các form trên bảng nổi (Lấy thông tin tướng, Thống kê tướng, Chiến khu).
- Ô tick, nút chọn và nút trong hộp thoại dùng màu tím của app thay cho màu xanh mặc định của Android. Ở giao diện Tối, màu tím được làm sáng hơn để dễ đọc trên nền tối.

### Chỉnh giao diện

- **Lịch sử:** mỗi lượt có nhãn màu cho trạng thái: Hoàn tất (xanh lá), Tạm dừng (vàng), Đang chạy (tím), Đã kết thúc (xám). Thư mục tướng có nhãn "Tên từ ảnh" hoặc "Cần đặt tên". Lượt có tên riêng có thêm nhãn loại tác vụ (Thông tin tướng, Thống kê, Chiến khu).
- **Danh sách tác vụ trên bảng nổi:** mỗi tác vụ có tên ngắn và một dòng mô tả. Ô tìm kiếm tìm theo cả hai.
- **Các form:** các nhóm có tiêu đề nhỏ: Cách lấy, Tùy chọn chụp, Vào game (Thống kê tướng: Bảng, Chế độ; Chiến khu: Số tướng, Bảng cần chụp).
- **Bảng nổi** đậm nền hơn, nên chữ không bị lẫn với màn hình game phía sau.

### Kiểm tra

- **Trên máy ảo CI**, UiSmoke chọn giao diện Tối rồi chụp màn Lịch sử, danh sách tác vụ và form Lấy thông tin tướng. Nó kiểm tra độ sáng trung bình của màn hình là tối.
- Toàn bộ kiểm tra cũ vẫn chạy với tên mục mới trong danh sách tác vụ.


Ảnh gốc Cờ Liên Quân: chọn **Ảnh từ Pictures → Chọn / đổi thư mục Pictures → Bộ nhớ trong/Pictures** một lần, rồi bật các nhóm tải ảnh trong macro Cờ. Sau **Save Photo → Sure**, ảnh được tự chép vào thư viện app theo nhóm; file xuất hiện chậm được tự thử lại. Mở ảnh tại **Ảnh từ Pictures → Mở ảnh gốc đã nhập trong Lịch sử**.

Sau mỗi lượt hoàn tất, có thể tự lưu vào ZIP đích hoặc thư mục Google Drive và gửi câu lệnh đã lưu trong ChatGPT → **dot của bạn**. Cấu hình tại **Sau khi hoàn tất** trên màn hình chính. App xác minh nội dung ZIP đọc lại và đợi thông báo tải thành công từ Drive có đúng tên file trước khi mở ChatGPT. Cần bật thông báo Drive và giữ dịch vụ Trợ năng hoạt động. Nếu Drive không phát thông báo xác nhận đúng tên ZIP, trạng thái giữ ở chờ và chưa gửi lệnh. Chưa xác nhận được lần gửi thì app không tự gửi trùng.

## Chỉnh từng bước trên điện thoại

Trên bảng nổi, chọn macro rồi bấm **Chỉnh từng bước**. App tạm dừng lượt thu thập, mở game theo luồng vào sảnh và chạy macro đã chọn ở chế độ chỉnh. Khi tới thao tác chạm/vuốt tiếp theo, app dừng **trước khi thực hiện**, vẽ đúng điểm mà macro sắp dùng và mở bảng điều khiển. Đây là luồng thực thi của macro, không phải danh sách tọa độ mẫu. Nếu cần đăng nhập, hãy đăng nhập bằng tay; chế độ chỉnh không nhập tài khoản hay mật khẩu.

- Kéo vòng tròn **Chạm**, hoặc kéo riêng **Đầu / Cuối** của đường vuốt. Kéo thanh **Kéo bảng để di chuyển** để đặt bảng điều khiển ở vị trí khác; **Đổi góc bảng** chuyển bảng lên hoặc xuống. Kéo điểm hay kéo bảng không chạm game và không tự lưu.
- **Lưu** ghi hiệu chỉnh của thao tác hiện tại vào máy và vẫn giữ macro tạm dừng. Hiệu chỉnh đã lưu được ưu tiên ở lần chạy thu thập bình thường cũng như lần chỉnh tiếp theo; đóng/mở lại ứng dụng không làm mất điểm đã lưu.
- **Chạy bước / Tiếp** thực hiện đúng thao tác đang xem với các điểm đang hiển thị, rồi tiếp tục logic nhận diện của macro để dừng trước thao tác chạm/vuốt kế tiếp. Nút này không tự lưu thay đổi: muốn dùng lại ở những lần sau, bấm **Lưu** trước. Không có nút chuyển qua một danh mục bước hoặc tự quay ngược màn hình game.
- **Mặc định** xóa hiệu chỉnh đã lưu của thao tác trên cấu hình màn hình hiện tại và đưa điểm về mặc định của lần nhận diện hiện tại; không thực hiện thao tác. **Hủy / Dừng** kết thúc chế độ chỉnh, bỏ các thay đổi chưa lưu và giữ những điểm đã lưu.
- Với điểm nhận diện từ chữ/hình hoặc đường vuốt phụ thuộc vị trí danh sách, app dùng hình học vừa nhận diện của thao tác thật. Hiệu chỉnh tương đối được áp dụng lên vị trí mới, không tái sử dụng tọa độ tuyệt đối của một mục khác. Nếu không nhận ra màn hình hoặc không thể thực hiện an toàn, luồng dừng thay vì tự bấm một điểm đoán.

Chế độ chỉnh không tạo ảnh thu thập, không tải ảnh gốc bằng **Save Photo / Sure**, không xuất hoặc gửi kết quả và không ghi đè tiến trình/lịch sử lượt thu thập. Các bước cần đọc màn hình vẫn có thể chụp tạm để nhận diện; bảng chỉnh được ẩn trong lúc chụp hoặc thực hiện thao tác. Các bước đăng nhập tự động và thao tác không được hỗ trợ sẽ không được giả lập bằng điểm chạm mẫu.

Hiệu chỉnh được lưu riêng theo kích thước màn hình, chiều xoay và phần viền hệ thống. Điện thoại khác có thể chỉnh trực tiếp mà không sửa APK; app không tự chuyển hiệu chỉnh giữa các kích thước. Xoay màn hình kết thúc chế độ chỉnh để tránh lưu nhầm cấu hình; mở lại **Chỉnh từng bước** để bắt đầu lại từ luồng vào sảnh. Chế độ chỉnh luôn bắt đầu lại macro, không tiếp tục một con trỏ chỉnh đã lưu. Điểm không có hiệu chỉnh hợp lệ vẫn dùng mặc định của macro.


### v0.13.13: bảng chỉnh gọn và không chặn điều hướng điện thoại

- Bảng chỉnh không chiếm tiêu điểm và luôn chừa vùng thanh hệ thống, cử chỉ điều hướng và phần khuyết màn hình, kể cả khi game ẩn thanh điều hướng. Có thể dùng **Home / Đa nhiệm** mà không phải đóng bảng trước.
- Trong lúc chỉnh, bảng điều khiển thu thập thông thường tạm ẩn; chỉ còn bảng chỉnh nhỏ, có thể kéo sang vị trí khác. Vùng game ngoài các điểm chỉnh vẫn được chặn để tránh vô tình đổi màn hình khi đang giữ một thao tác chờ. Bấm **Hủy** để thao tác trực tiếp trong game.
- Kiểm tra ứng dụng đang mở bỏ qua các cửa sổ trợ năng của chính Collector, không nhầm bảng chỉnh thành ứng dụng ngoài game. Vẫn từ chối thao tác nếu một ứng dụng khác ở phía trước hoặc không xác định được màn hình đích; không dựa vào tên game đã thấy từ trước. Chế độ chỉnh yêu cầu game toàn màn hình; chia đôi màn hình, cửa sổ nổi và PiP không được hỗ trợ.
- Rời game bằng Home / Đa nhiệm hủy thao tác đang chờ; quay lại không tự chạy tiếp. Nút **Chạy bước / Tiếp** vẫn chỉ chạy đúng một thao tác, không thử lại ngầm. Điểm đã **Lưu**, ưu tiên hiệu chỉnh và tiến trình thu thập bình thường được giữ nguyên.
- Bộ kiểm thử thiết bị dùng một ứng dụng game giả riêng trong CI: nhận sự kiện chạm/vuốt Android thật, kiểm tra nút Home / Đa nhiệm thật, ứng dụng sai, hủy và không phát lại. Ứng dụng giả không nằm trong APK Collector và không đăng nhập game.
