#!/usr/bin/env python3
"""Merges the per-image answers of extract.py into one record per hero.

    python3 extraction/merge.py out/*/results.jsonl --out heroes
    python3 extraction/merge.py first-run/heroes.json redo/heroes.json --out heroes

Inputs are results.jsonl files of extract.py, or heroes.json files of earlier merges (to join a run with a
later one that redid some heroes: for each hero the record with fewer failed images is kept).

Writes heroes.json (one entry per hero folder, in collection order), the same as two spreadsheets
(heroes.csv: a hero per row with its stats; skills.csv: a skill per row) and summary.md, which lists every
hero with its main fields and the images that failed or came back incomplete.
"""
import argparse
import csv
import json
import re
import unicodedata
from pathlib import Path

SLOTS = {"passive": "noi_tai", "skill-1": "chieu_1", "skill-2": "chieu_2", "skill-3": "chieu_3"}
SKILL_FIELDS = ("ten_chieu", "nhan", "hoi_chieu", "tieu_hao", "mo_ta", "bang_cap")
SLOT_NAMES = {"noi_tai": "Nội tại", "chieu_1": "Chiêu 1", "chieu_2": "Chiêu 2", "chieu_3": "Chiêu 3"}


def write_csv(path: Path, header: list, rows: list) -> None:
    # UTF-8 with a byte order mark, so Excel shows Vietnamese accents.
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(rows)


def plain(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower()).replace("đ", "d")
    return "".join(c for c in text if not unicodedata.combining(c))


def fix_name(text: str, name: str) -> str:
    """The hero's name written with wrong accents ("Diêu Thuyền" for "Điêu Thuyền") becomes the name read on
    the hero page. Only a run of words that matches the whole name without accents is replaced."""
    if not text or not name:
        return text
    size, target = len(name.split()), plain(name)
    words = list(re.finditer(r"\w+(?:['’]\w+)?", text))
    for i in range(len(words) - size, -1, -1):
        start, end = words[i].start(), words[i + size - 1].end()
        span = text[start:end]
        if span != name and plain(re.sub(r"\s+", " ", span)) == target:
            text = text[:start] + name + text[end:]
    return text


def number(image: str) -> int:
    found = re.search(r"hero-(\d{3})-", image)
    return int(found.group(1)) if found else 9999


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("results", nargs="+", type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    heroes = {}
    for results in args.results:
        if results.suffix == ".json":
            for record in json.loads(results.read_text(encoding="utf-8")):
                old = heroes.get(record["thu_muc"])
                if old is None or len(record["loi"]) <= len(old["loi"]):
                    heroes[record["thu_muc"]] = record
            continue
        for line in results.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            if r["kind"] not in ("overview", "attributes", "skill"):
                continue
            folder = Path(r["image"]).parent.name
            hero = heroes.setdefault(folder, {"so_thu_tu": number(r["image"]), "thu_muc": folder, "loi": []})
            answer = r.get("answer") or {}
            if r.get("error") or "raw" in answer:
                hero["loi"].append(f"{Path(r['image']).name}: {r.get('error') or 'JSON không đọc được'}")
                continue
            if r.get("finish") == "length":
                hero["loi"].append(f"{Path(r['image']).name}: câu trả lời bị cắt vì quá dài")
            if r["kind"] == "overview":
                # A later reading of the page fills or corrects fields but never empties one.
                for key in ("ten_tuong", "danh_hieu", "so_truong"):
                    if answer.get(key) or key not in hero:
                        hero[key] = answer.get(key, "")
            elif r["kind"] == "attributes":
                hero["gioi_thieu"] = answer.get("mo_ta_tuong", "")
                hero["chi_so"] = {c.get("ten", ""): c.get("gia_tri", "") for c in answer.get("chi_so", [])}
            else:
                slot = re.search(r"(passive|skill-\d)-(detail|summary)$", Path(r["image"]).stem)
                key = SLOTS.get(slot.group(1)) if slot else None
                if key:
                    skill = hero.setdefault(key, {})
                    if slot.group(2) == "summary":
                        skill["tom_tat"] = answer.get("mo_ta", "")
                    else:
                        skill.update({k: answer.get(k) for k in SKILL_FIELDS})

    ordered = sorted(heroes.values(), key=lambda h: (h["so_thu_tu"], h["thu_muc"]))
    for h in ordered:
        name = h.get("ten_tuong", "")
        h["gioi_thieu"] = fix_name(h.get("gioi_thieu", ""), name)
        for key in SLOTS.values():
            for field in ("mo_ta", "tom_tat"):
                if h.get(key, {}).get(field):
                    h[key][field] = fix_name(h[key][field], name)
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "heroes.json").write_text(json.dumps(ordered, ensure_ascii=False, indent=2), encoding="utf-8")
    stats = list(dict.fromkeys(name for h in ordered for name in h.get("chi_so", {})))
    write_csv(args.out / "heroes.csv", ["#", "Thư mục", "Tên", "Danh hiệu", "Sở trường", "Giới thiệu"] + stats,
              [[h["so_thu_tu"], h["thu_muc"], h.get("ten_tuong", ""), h.get("danh_hieu", ""), h.get("so_truong", ""),
                h.get("gioi_thieu", "")] + [h.get("chi_so", {}).get(name, "") for name in stats] for h in ordered])
    write_csv(args.out / "skills.csv", ["#", "Tướng", "Vị trí", "Tên chiêu", "Nhãn", "Hồi chiêu", "Tiêu hao", "Mô tả", "Theo cấp"],
              [[h["so_thu_tu"], h.get("ten_tuong", ""), SLOT_NAMES[key], s.get("ten_chieu", ""), ", ".join(s.get("nhan") or []),
                s.get("hoi_chieu", ""), s.get("tieu_hao", ""), s.get("mo_ta", ""),
                "; ".join(f"{row.get('ten')}: {' / '.join(row.get('gia_tri') or [])}" for row in s.get("bang_cap") or [])]
               for h in ordered for key in SLOTS.values() for s in [h.get(key) or {}] if s])

    def cell(text) -> str:
        return re.sub(r"\s+", " ", str(text or "")).replace("|", "\\|")

    missing = [h for h in ordered if not h.get("ten_tuong") or any(k not in h for k in SLOTS.values())]
    lines = ["## Dữ liệu tướng", "", f"{len(ordered)} tướng · {sum(len(h['loi']) for h in ordered)} ảnh lỗi hoặc bị cắt · "
             f"{len(missing)} tướng thiếu trường", "",
             "| # | Thư mục | Tên | Danh hiệu | Sở trường | Nội tại | Chiêu 1 | Chiêu 2 | Chiêu 3 | Lỗi |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for h in ordered:
        skills = [cell((h.get(k) or {}).get("ten_chieu")) for k in SLOTS.values()]
        lines.append(f"| {h['so_thu_tu']} | {cell(h['thu_muc'])} | {cell(h.get('ten_tuong'))} | {cell(h.get('danh_hieu'))} | "
                     f"{cell(h.get('so_truong'))} | " + " | ".join(skills) + f" | {cell('; '.join(h['loi']))} |")
    # The folder is named from what the app read on the hero page; when the model read another name, one of them
    # is wrong (the app read "Apnette" for Annette, the model "Kahlil" for Kahlii), so both are listed for review.
    def slug(name: str) -> str:   # as the app names folders (HeroNames.slug)
        return re.sub(r"[^a-z0-9]+", "-", re.sub(r"['’]", "", plain(name))).strip("-")
    differ = [h for h in ordered if re.sub(r"-\d+$", "", h["thu_muc"]) != slug(h.get("ten_tuong", ""))]
    if differ:
        lines += ["", "**Tên cần xem lại** (thư mục theo tên app đọc, tên theo model): " +
                  ", ".join(f"{h['thu_muc']} → {h.get('ten_tuong') or '?'}" for h in differ)]
    (args.out / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(ordered)} heroes -> {args.out / 'heroes.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
