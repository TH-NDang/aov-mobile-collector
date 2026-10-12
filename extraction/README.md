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

**Trên ảnh thật** (Capheny: tổng quan, thuộc tính, nội tại, 3 chiêu), CPU 4 nhân:

| Model | Giây/ảnh chiêu | Kết quả |
|---|---|---|
| 4B | 81–129 | Tên chiêu, nhãn, hồi chiêu, tiêu hao, bảng Lv và 16 chỉ số đúng hết. Mô tả còn sai dấu: "làm chật", "lâm chấn" (làm chậm), "Cappheny" |
| 8B | 157–196 | Như 4B, và mô tả gần như đúng nguyên văn |

Lượt chạy toàn bộ dùng 8B:

- Workflow chia 129 tướng cho 20 job song song, mỗi job khoảng 6–7 tướng, ước 2–2,5 giờ.
- Job `merge` gộp kết quả thành `heroes.json`. Mỗi tướng gồm: tên, danh hiệu, sở trường, giới thiệu, chỉ số, nội tại, chiêu 1–3 (tên, nhãn, hồi chiêu, tiêu hao, mô tả, bảng cấp) và danh sách ảnh lỗi.
- Tên tướng app đã đọc (`hero.json`) được đưa vào prompt, để model không viết sai tên.

## Kết quả lượt chạy toàn bộ (release `assets`, 129 tướng)

Dữ liệu nằm trong `data/`:

- `heroes.json`: đầy đủ mọi trường.
- `heroes.csv`: mỗi tướng một dòng, kèm chỉ số.
- `skills.csv`: mỗi chiêu một dòng.
- `summary.md`: bảng tóm tắt.

Mở CSV bằng Excel sẽ hiện đúng dấu tiếng Việt.

- 774 ảnh, đọc bằng Qwen3-VL 8B trên 20 job, mỗi job 1–2 giờ. Không ảnh nào lỗi hay bị cắt.
- 129/129 tướng có tên, danh hiệu, sở trường, giới thiệu và 14–16 chỉ số. 387/387 chiêu có hồi chiêu và bảng theo cấp.
- 12 trang tổng quan có chữ sở trường sáng trên nền sáng bị bỏ sót, nên được đọc lại với vùng dòng "Sở trường" phóng 2,5 lần. Vùng này nay là mặc định.
- Tên cần xem lại (cuối `summary.md`):
  - Model đọc sai: "Kahlil" (đúng là Kahlii), "Zephy" (Zephys).
  - App đọc sai, model đúng: Billow, Annette, Qi, Iggy, Teeri.
- `flowborn-2` là dạng thứ hai của Flowborn, không phải ảnh trùng.

Tạo lại dữ liệu từ artifact của các lượt chạy:

```
python3 extraction/merge.py <lượt 38234306660>/heroes.json <lượt 38241910555>/heroes.json <đọc lại tổng quan>/results.jsonl --out data
```

