# Trích xuất chữ từ ảnh chụp (Qwen3-VL chạy trên CPU)

Workflow `.github/workflows/extract-text.yml` đọc chữ trong ảnh AOV Collector bằng Qwen3-VL. Model chạy qua `llama-server` của llama.cpp ngay trên runner GitHub Actions, không cần API key, ảnh không gửi ra ngoài.

## Cách chạy

- **Tự động:** push thay đổi trong `extraction/` hoặc workflow lên nhánh `claude/**`.
- **Chạy tay:** Actions → Extract text (Qwen3-VL) → Run workflow. Chạy tay chỉ dùng được khi workflow đã có trên nhánh mặc định.
  - `release`: tag của release chứa ảnh. Để trống thì lấy release mới nhất, kể cả bản nháp.
  - `models`: `2b`, `4b` hoặc `2b,4b`.
  - `limit`: số ảnh mỗi model, chia đều cho các loại màn hình. `0` là tất cả.
- **Nguồn ảnh:** file PNG/JPG hoặc ZIP xuất từ app, đính kèm vào release. Không có release thì workflow đọc ảnh mẫu trong `extraction/samples`.
- **Kết quả:** artifact `extracted-text`, gồm mỗi ảnh một file JSON, `results.jsonl` và `summary.md`. Bảng tóm tắt cũng hiện trong trang của lượt chạy.

## Cách đọc

- **Loại màn hình** lấy từ tên file app đặt: `overview`, `attributes`, `skill-N-detail/summary`, `page-NN-list`. Mỗi loại có prompt và JSON schema riêng; server ép câu trả lời theo schema nên luôn ra JSON hợp lệ.
- **UID tài khoản:** dải dưới cùng của ảnh, chỗ game in UID, bị cắt bỏ trước khi đọc.
- **Vùng chữ:** với loại đã biết bố cục (hiện là `overview`), chỉ các vùng có chữ được cắt ra ở độ phân giải gốc rồi phóng 1,5 lần, thay vì thu nhỏ cả màn hình. Chữ nhỏ giữ được dấu, và model nhận ít token ảnh hơn. Toạ độ các vùng nằm trong `REGIONS` của `extract.py`.
- **Tesseract (`vie`)** chạy cùng các ảnh đó để so sánh.

## Kết quả thử trên CPU 4 nhân (3 trang tướng mẫu)

| Cách | Giây/ảnh | Tên tướng | Danh hiệu, sở trường |
|---|---|---|---|
| Qwen3-VL 2B, cả màn hình 1600px | ~47 | 2/3 (nhầm danh hiệu thành tên) | sai dấu: "Soái tác", "Tiên đạo" |
| Qwen3-VL 4B, cả màn hình 1600px | ~66 | 3/3 | sai dấu: "Soái tác", "Tiên đạo", "Thành quang" |
| Qwen3-VL 4B, vùng chữ phóng 1,5x | ~50 | 3/3 | đúng "Soái tặc", "Tiền đạo", "Thánh quang đạo sư"; còn sai "Câu ria" (đúng là "Cấu rỉa") |
| Tesseract, vùng chữ | <1 | 0/3 | phần lớn ra ký tự rác trên chữ cách điệu |

**Cần làm tiếp khi có ảnh thật** của bảng thuộc tính và bảng chiêu:

- Thêm vùng chữ cho hai loại này vào `REGIONS`.
- Chỉnh prompt cho từng loại.

**Trên runner GitHub** (4 nhân), tốc độ tuỳ CPU được cấp:

| Model | Runner chậm | Runner nhanh |
|---|---|---|
| 4B | ~50 giây/trang tướng | ~29 giây/trang tướng |
| 2B | ~28 giây/trang tướng | ~18 giây/trang tướng |

Tesseract cho kết quả không dùng được.

**Toàn bộ khoảng 1.300 ảnh** với 4B mất cỡ 10–18 giờ CPU, nên phải chia thành nhiều job song song.
