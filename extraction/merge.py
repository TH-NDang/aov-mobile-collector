#!/usr/bin/env python3
"""Merges the per-image answers of extract.py into one record per hero.

    python3 extraction/merge.py out/*/results.jsonl --out heroes

Writes heroes.json (one entry per hero folder, in collection order) and summary.md, which lists every
hero with its main fields and the images that failed or came back incomplete.
"""
import argparse
import json
import re
from pathlib import Path

SLOTS = {"passive": "noi_tai", "skill-1": "chieu_1", "skill-2": "chieu_2", "skill-3": "chieu_3"}
SKILL_FIELDS = ("ten_chieu", "nhan", "hoi_chieu", "tieu_hao", "mo_ta", "bang_cap")


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
        for line in results.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            if r["kind"] not in ("overview", "attributes", "skill"):
                continue
            folder = str(Path(r["image"]).parent)
            hero = heroes.setdefault(folder, {"so_thu_tu": number(r["image"]), "thu_muc": Path(folder).name, "loi": []})
            answer = r.get("answer") or {}
            if r.get("error") or "raw" in answer:
                hero["loi"].append(f"{Path(r['image']).name}: {r.get('error') or 'JSON không đọc được'}")
                continue
            if r.get("finish") == "length":
                hero["loi"].append(f"{Path(r['image']).name}: câu trả lời bị cắt vì quá dài")
            if r["kind"] == "overview":
                for key in ("ten_tuong", "danh_hieu", "so_truong"):
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
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "heroes.json").write_text(json.dumps(ordered, ensure_ascii=False, indent=2), encoding="utf-8")

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
    (args.out / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(ordered)} heroes -> {args.out / 'heroes.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
