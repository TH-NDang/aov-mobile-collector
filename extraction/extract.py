#!/usr/bin/env python3
"""Reads the text in AOV Collector screenshots with a vision model served by llama.cpp's llama-server.

The kind of screen comes from the file name the app gives each screenshot (overview, attributes, skill
detail or summary, list page). Each kind has its own prompt and JSON schema; the server constrains the
answer to that schema, so even a small model returns valid JSON. With --engine tesseract the same images
go through Tesseract (Vietnamese) instead, as a baseline.

    python3 extraction/extract.py --images input --out out/2b --label "Qwen3-VL 2B"

Writes one JSON file per image, results.jsonl with every answer and timing, and summary.md.
"""
import argparse
import base64
import io
import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

from PIL import Image

IMAGE_TYPES = {".png", ".jpg", ".jpeg", ".webp"}
# Bounded so a model that starts repeating a line still ends with valid JSON.
LINES = {"type": "array", "items": {"type": "string"}, "maxItems": 60}

KINDS = {
    "overview": (
        "Hai ảnh được cắt từ trang thông tin một tướng trong game Liên Quân Mobile. Ảnh 1 là bảng bên trái "
        "(tab Chi tiết/Trang phục, Sở trường, các chỉ số). Ảnh 2 là góc phải: dòng chữ nhỏ ở trên là danh hiệu, "
        "dòng chữ lớn ở dưới là tên tướng. Chép lại mọi dòng chữ tiếng Việt, giữ đúng dấu, vào van_ban. "
        "so_truong là chữ nằm bên phải chữ \"Sở trường\" (ví dụ Tiền đạo), không phải chính chữ \"Sở trường\". "
        "Ô nào không thấy thì để chuỗi rỗng.",
        {"van_ban": LINES, "ten_tuong": {"type": "string"}, "danh_hieu": {"type": "string"}, "so_truong": {"type": "string"}},
    ),
    "attributes": (
        "Ảnh là bảng thuộc tính của một tướng trong game Liên Quân Mobile: trên cùng là đoạn giới thiệu tướng, "
        "dưới là \"Thuộc tính cơ bản\" và \"Thuộc tính tấn công\" gồm các cặp tên và giá trị (ví dụ Máu 3379, "
        "Giáp 134 | 0%, Tầm đánh Đánh xa). mo_ta_tuong là đoạn giới thiệu, chép nguyên văn. chi_so có một phần tử "
        "cho mỗi chỉ số, ví dụ {\"ten\": \"Máu\", \"gia_tri\": \"3379\"} rồi {\"ten\": \"Công vật lý\", \"gia_tri\": \"169\"}; "
        "không gộp nhiều chỉ số vào một phần tử, không lấy tiêu đề nhóm làm tên. Giữ đúng dấu tiếng Việt và số.",
        {"mo_ta_tuong": {"type": "string"}, "chi_so": {"type": "array", "maxItems": 24, "items": {"type": "object",
            "properties": {"ten": {"type": "string"}, "gia_tri": {"type": "string"}}, "required": ["ten", "gia_tri"]}}},
    ),
    "skill": (
        "Ảnh là bảng mô tả một chiêu (hoặc nội tại) của tướng trong game Liên Quân Mobile. ten_chieu là dòng tiêu "
        "đề trên cùng, bỏ số thứ tự trong vòng tròn. nhan là các nhãn nhỏ dưới tiêu đề (ví dụ Vật lý, ST vùng, "
        "Đặc biệt). hoi_chieu là giá trị sau chữ \"H.chiêu\" (ví dụ 0.2s). tieu_hao là dòng tiêu hao kèm nhãn "
        "(ví dụ \"Nội năng cần: 0\" hoặc \"Năng lượng: 60\"). mo_ta là toàn bộ đoạn mô tả, chép nguyên văn. "
        "bang_cap là từng hàng của bảng theo cấp Lv 1, Lv 2…: tên hàng và các giá trị theo thứ tự cấp. Bỏ qua hai "
        "nút \"Tóm tắt\", \"Chi tiết\" ở góc trên bên phải và chữ mờ in chìm ở nền (tên, danh hiệu tướng). Nội tại "
        "thường không có H.chiêu, tiêu hao và bảng cấp: khi đó để rỗng. Giữ đúng dấu tiếng Việt và số, không tự thêm chữ.",
        {"ten_chieu": {"type": "string"}, "nhan": {"type": "array", "items": {"type": "string"}, "maxItems": 6},
         "hoi_chieu": {"type": "string"}, "tieu_hao": {"type": "string"}, "mo_ta": {"type": "string"},
         "bang_cap": {"type": "array", "maxItems": 8, "items": {"type": "object", "properties": {
             "ten": {"type": "string"}, "gia_tri": {"type": "array", "items": {"type": "string"}, "maxItems": 8}},
             "required": ["ten", "gia_tri"]}}},
    ),
    "list": (
        "Đây là danh sách tướng trong game Liên Quân Mobile. Liệt kê tên tướng in trên từng thẻ, theo thứ tự từ "
        "trái sang phải, từ trên xuống dưới, mỗi tên một lần. Bỏ qua các chữ \"Chưa có\", \"Miễn phí\" và các con số.",
        {"ten_tuong": {"type": "array", "items": {"type": "string"}, "maxItems": 30}},
    ),
    "other": (
        "Chép lại mọi dòng chữ tiếng Việt nhìn thấy trong ảnh chụp màn hình game này, giữ nguyên dấu và số, "
        "theo thứ tự đọc.",
        {"van_ban": LINES},
    ),
}


def kind_of(path: Path) -> str:
    name = path.stem
    if name.endswith("-overview"):
        return "overview"
    if name.endswith("-attributes"):
        return "attributes"
    if re.search(r"(passive|skill-\d)-(detail|summary)$", name):
        return "skill"
    if "-page-" in name and name.endswith("-list"):
        return "list"
    return "other"


def pick(images: list, limit: int) -> list:
    """With a limit, take the kinds in turn so a short test still covers every kind of screen."""
    if limit <= 0 or limit >= len(images):
        return images
    groups = {}
    for p in images:
        groups.setdefault(kind_of(p), []).append(p)
    chosen = []
    while len(chosen) < limit:
        for kind in list(groups):
            if groups[kind]:
                chosen.append(groups[kind].pop(0))
                if len(chosen) == limit:
                    break
            else:
                del groups[kind]
    return chosen


# Areas with text on a 2400x1080 screen, as fractions, and how much to enlarge them. They are read at full
# resolution instead of the whole screen scaled down: game text keeps its accents, with fewer image tokens.
REGIONS = {
    "overview": ([(0.06, 0.21, 0.29, 0.57), (0.71, 0.20, 0.96, 0.40)], 1.5),   # info panel; title and name
    "attributes": ([(0.50, 0.08, 0.93, 0.80)], 1.0),                            # introduction and stats
    "skill": ([(0.445, 0.015, 0.99, 0.90)], 1.0),                               # name, tags, cooldown, text, levels
}


def prepare(path: Path, kind: str, max_width: int, crops: bool) -> list:
    """Drops the bottom strip, where the game prints the account UID, then cuts the text areas or scales down."""
    image = Image.open(path).convert("RGB")
    image = image.crop((0, 0, image.width, min(image.height, round(image.width * 1080 / 2400 * 0.955))))
    if crops and kind in REGIONS:
        # Heights follow the width, so a screenshot already cut at the bottom keeps the same areas.
        boxes, zoom = REGIONS[kind]
        w, h, factor = image.width, image.width * 1080 / 2400, zoom * 2400 / image.width
        parts = []
        for x0, y0, x1, y1 in boxes:
            part = image.crop((round(x0 * w), round(y0 * h), round(x1 * w), min(image.height, round(y1 * h))))
            parts.append(part.resize((round(part.width * factor), round(part.height * factor)), Image.LANCZOS))
        return parts
    if image.width > max_width:
        image = image.resize((max_width, round(image.height * max_width / image.width)), Image.LANCZOS)
    return [image]


def data_url(image: Image.Image) -> str:
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=92)
    return "data:image/jpeg;base64," + base64.b64encode(buffer.getvalue()).decode()


def hero_name(path: Path) -> str:
    """The name the app read for this hero folder, if any; it keeps the model from misspelling the hero."""
    try:
        info = json.loads((path.parent / "hero.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return ""
    return info.get("name", "") if info.get("nameSource") in ("ocr", "manual") else ""


def ask_model(server: str, images: list, kind: str, max_tokens: int, name: str = "") -> tuple:
    prompt, properties = KINDS[kind]
    if len(images) == 1 and kind == "overview":
        prompt = KINDS["other"][0] + " Sau đó điền các ô còn lại nếu thấy."
    if name and kind in ("attributes", "skill"):
        prompt = f"Tướng trong ảnh tên là {name}. " + prompt
    body = {
        "messages": [{"role": "user", "content": [{"type": "image_url", "image_url": {"url": data_url(i)}} for i in images]
                      + [{"type": "text", "text": prompt + " Trả lời bằng JSON."}]}],
        "response_format": {"type": "json_schema", "json_schema": {"name": kind, "schema": {
            "type": "object", "properties": properties, "required": list(properties)}}},
        "temperature": 0,
        "max_tokens": max_tokens,
        "cache_prompt": False,
    }
    request = urllib.request.Request(server.rstrip("/") + "/v1/chat/completions", data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=1800) as response:
        reply = json.load(response)
    text = reply["choices"][0]["message"]["content"]
    try:
        answer = json.loads(text)
    except json.JSONDecodeError:
        answer = {"raw": text}
    return answer, reply.get("timings", {}), reply["choices"][0].get("finish_reason")


def ask_tesseract(images: list) -> dict:
    lines = []
    for image in images:
        buffer = io.BytesIO()
        image.save(buffer, "PNG")
        result = subprocess.run(["tesseract", "stdin", "stdout", "-l", "vie", "--psm", "11"], input=buffer.getvalue(),
                                capture_output=True, check=True)
        lines += [line.strip() for line in result.stdout.decode("utf-8", "replace").splitlines() if line.strip()]
    return {"van_ban": lines}


def headline(kind: str, answer: dict) -> str:
    """The field a reader checks first for each kind of screen."""
    key = {"overview": "ten_tuong", "skill": "ten_chieu"}.get(kind)
    if key and answer.get(key):
        return str(answer[key])
    if kind == "attributes" and answer.get("chi_so"):
        return "; ".join(f"{c.get('ten')}: {c.get('gia_tri')}" for c in answer["chi_so"][:4])
    if kind == "list" and answer.get("ten_tuong"):
        return ", ".join(answer["ten_tuong"][:6])
    lines = answer.get("van_ban") or [answer.get("raw", "")]
    return " / ".join(lines[:3])


def cell(text: str, size: int = 90) -> str:
    text = re.sub(r"\s+", " ", text).replace("|", "\\|")
    return text if len(text) <= size else text[: size - 1] + "…"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--images", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--label", default="")
    parser.add_argument("--engine", choices=["llama", "tesseract"], default="llama")
    parser.add_argument("--server", default="http://127.0.0.1:8080")
    parser.add_argument("--limit", type=int, default=0, help="images to read, spread over the kinds of screen (0: all)")
    parser.add_argument("--max-width", type=int, default=1600, help="screens without text areas are scaled to this width")
    parser.add_argument("--no-crops", dest="crops", action="store_false", help="send whole screens, scaled down")
    parser.add_argument("--max-tokens", type=int, default=1600)
    parser.add_argument("--kinds", default="", help="comma-separated kinds of screen to read (default: all)")
    parser.add_argument("--shard", type=int, default=0, help="with --shards, read only this share of the heroes")
    parser.add_argument("--shards", type=int, default=1)
    args = parser.parse_args()

    images = sorted(p for p in args.images.rglob("*") if p.suffix.lower() in IMAGE_TYPES)
    if args.kinds:
        images = [p for p in images if kind_of(p) in args.kinds.split(",")]
    if args.shards > 1:
        # A hero's screens stay in one share; folders are dealt out in turn so shares get similar work.
        folders = sorted({p.parent for p in images})
        mine = {f for i, f in enumerate(folders) if i % args.shards == args.shard}
        images = [p for p in images if p.parent in mine]
    images = pick(images, args.limit)
    if not images:
        print(f"No images under {args.images}", file=sys.stderr)
        return 1
    args.out.mkdir(parents=True, exist_ok=True)
    rows = []
    with open(args.out / "results.jsonl", "w", encoding="utf-8") as results:
        for number, path in enumerate(images, 1):
            kind = kind_of(path)
            parts = prepare(path, kind, args.max_width, args.crops)
            started = time.monotonic()
            timings, finish, error = {}, None, None
            try:
                if args.engine == "tesseract":
                    answer = ask_tesseract(parts)
                else:
                    answer, timings, finish = ask_model(args.server, parts, kind, args.max_tokens, hero_name(path))
            except Exception as e:  # keep going: one bad image should not lose the rest of the run
                answer, error = {}, f"{type(e).__name__}: {e}"
            seconds = time.monotonic() - started
            record = {"image": str(path.relative_to(args.images)), "kind": kind, "sizes": [list(p.size) for p in parts],
                      "seconds": round(seconds, 1), "finish": finish, "error": error, "answer": answer,
                      "prompt_tokens": timings.get("prompt_n"), "output_tokens": timings.get("predicted_n")}
            results.write(json.dumps(record, ensure_ascii=False) + "\n")
            results.flush()
            target = args.out / (str(path.relative_to(args.images)).replace("/", "__") + ".json")
            target.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
            rows.append(record)
            print(f"[{number}/{len(images)}] {record['image']} ({kind}) {seconds:.0f}s"
                  f" {error or headline(kind, answer)}", flush=True)

    done = [r for r in rows if not r["error"]]
    total = sum(r["seconds"] for r in rows)
    lines = [f"## {args.label or args.engine}", "",
             f"{len(done)}/{len(rows)} ảnh đọc được · trung bình {total / max(1, len(rows)):.0f} giây/ảnh · " + ("đọc theo vùng chữ" if args.crops else f"cả màn hình thu về {args.max_width}px"), "",
             "| Ảnh | Loại | Giây | Token vào/ra | Kết quả chính |", "|---|---|---|---|---|"]
    for r in rows:
        tokens = f"{r['prompt_tokens']}/{r['output_tokens']}" if r["prompt_tokens"] else ""
        lines.append(f"| {cell(r['image'], 60)} | {r['kind']} | {r['seconds']:.0f} | {tokens} | "
                     f"{cell(r['error'] or headline(r['kind'], r['answer']))} |")
    (args.out / "summary.md").write_text("\n".join(lines) + "\n\n", encoding="utf-8")
    return 0 if done else 1


if __name__ == "__main__":
    sys.exit(main())
