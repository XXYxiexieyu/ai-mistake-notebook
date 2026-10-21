#!/usr/bin/env python3
"""Build the bundled offline knowledge-tree asset from public contents pages."""

from __future__ import annotations

import html
import json
import re
import sys
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class BookSource:
    subject: str
    publisher: str
    title: str
    url: str


BOOKS = [
    BookSource("语文", "统编版（人教社出版）", "必修上册", "http://www.dzkbw.com/books/rjb/yuwen/gzbxgysc/"),
    BookSource("语文", "统编版（人教社出版）", "必修下册", "http://www.dzkbw.com/books/rjb/yuwen/gzbxxc/"),
    BookSource("语文", "统编版（人教社出版）", "选择性必修上册", "http://www.dzkbw.com/books/rjb/yuwen/gzxzxbxsc/"),
    BookSource("语文", "统编版（人教社出版）", "选择性必修中册", "http://www.dzkbw.com/books/rjb/yuwen/gzxzxbxzc/"),
    BookSource("语文", "统编版（人教社出版）", "选择性必修下册", "http://www.dzkbw.com/books/rjb/yuwen/gzxzxbxxc/"),
    BookSource("数学", "人教B版2019", "必修第一册", "http://www.dzkbw.com/books/rjb/shuxue/gzbbx1/"),
    BookSource("数学", "人教B版2019", "必修第二册", "http://www.dzkbw.com/books/rjb/shuxue/gzbbx2/"),
    BookSource("数学", "人教B版2019", "必修第三册", "http://www.dzkbw.com/books/rjb/shuxue/gzbbx3/"),
    BookSource("数学", "人教B版2019", "必修第四册", "http://www.dzkbw.com/books/rjb/shuxue/gzbbx4/"),
    BookSource("数学", "人教B版2019", "选择性必修第一册", "http://www.dzkbw.com/books/rjb/shuxue/gzbxzxbx1/"),
    BookSource("数学", "人教B版2019", "选择性必修第二册", "http://www.dzkbw.com/books/rjb/shuxue/gzbxzxbx2/"),
    BookSource("数学", "人教B版2019", "选择性必修第三册", "http://www.dzkbw.com/books/rjb/shuxue/gzbxzxbx3/"),
    BookSource("英语", "北师大版2019", "必修第一册", "http://www.dzkbw.com/books/bsd/yingyu/gzbxd1c/"),
    BookSource("英语", "北师大版2019", "必修第二册", "http://www.dzkbw.com/books/bsd/yingyu/gzbxd2c/"),
    BookSource("英语", "北师大版2019", "必修第三册", "http://www.dzkbw.com/books/bsd/yingyu/gzbxd3c/"),
    BookSource("英语", "北师大版2019", "选择性必修第一册", "http://www.dzkbw.com/books/bsd/yingyu/gzxzxbxd1c/"),
    BookSource("英语", "北师大版2019", "选择性必修第二册", "http://www.dzkbw.com/books/bsd/yingyu/gzxzxbxd2c/"),
    BookSource("英语", "北师大版2019", "选择性必修第三册", "http://www.dzkbw.com/books/bsd/yingyu/gzxzxbxd3c/"),
    BookSource("英语", "北师大版2019", "选择性必修第四册", "http://www.dzkbw.com/books/bsd/yingyu/gzxzxbxd4c/"),
    BookSource("物理", "人教版2019", "必修第一册", "http://www.dzkbw.com/books/rjb/wuli/pgzd1c/"),
    BookSource("物理", "人教版2019", "必修第二册", "http://www.dzkbw.com/books/rjb/wuli/pgzd2c/"),
    BookSource("物理", "人教版2019", "必修第三册", "http://www.dzkbw.com/books/rjb/wuli/pgzd3c/"),
    BookSource("物理", "人教版2019", "选择性必修第一册", "http://www.dzkbw.com/books/rjb/wuli/pgzxbd1c/"),
    BookSource("物理", "人教版2019", "选择性必修第二册", "http://www.dzkbw.com/books/rjb/wuli/pgzxbd2c/"),
    BookSource("物理", "人教版2019", "选择性必修第三册", "http://www.dzkbw.com/books/rjb/wuli/pgzxbd3c/"),
    BookSource("化学", "人教版2019", "必修第一册", "http://www.dzkbw.com/books/rjb/huaxue/pgzbxd1c/"),
    BookSource("化学", "人教版2019", "必修第二册", "http://www.dzkbw.com/books/rjb/huaxue/pgzbxd2c/"),
    BookSource("化学", "人教版2019", "选择性必修1 化学反应原理", "http://www.dzkbw.com/books/rjb/huaxue/pgzxzxbxd1c/"),
    BookSource("化学", "人教版2019", "选择性必修2 物质结构与性质", "http://www.dzkbw.com/books/rjb/huaxue/pgzxzxbxd2c/"),
    BookSource("化学", "人教版2019", "选择性必修3 有机化学基础", "http://www.dzkbw.com/books/rjb/huaxue/pgzxzxbxd3c/"),
    BookSource("生物", "人教版2019", "必修1 分子与细胞", "http://www.dzkbw.com/books/rjb/shengwu/pgzbx1/"),
    BookSource("生物", "人教版2019", "必修2 遗传与进化", "http://www.dzkbw.com/books/rjb/shengwu/pgzbx2/"),
    BookSource("生物", "人教版2019", "选择性必修1 稳态与调节", "http://www.dzkbw.com/books/rjb/shengwu/pgzxzxbx1/"),
    BookSource("生物", "人教版2019", "选择性必修2 生物与环境", "http://www.dzkbw.com/books/rjb/shengwu/pgzxzxbx2/"),
    BookSource("生物", "人教版2019", "选择性必修3 生物技术与工程", "http://www.dzkbw.com/books/rjb/shengwu/pgzxzxbx3/"),
]


ANCHOR_RE = re.compile(
    r'<a[^>]+target="_blank"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',
    re.IGNORECASE | re.DOTALL,
)
TAG_RE = re.compile(r"<[^>]+>")
SPACE_RE = re.compile(r"\s+")


def clean_text(value: str) -> str:
    text = html.unescape(TAG_RE.sub("", value))
    return SPACE_RE.sub(" ", text).strip()


def fetch(url: str) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 AI-Mistake-Notebook catalog builder",
            "Accept": "text/html",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read()
    for encoding in ("gb18030", "utf-8"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def parse_book(source: BookSource) -> dict:
    page = fetch(source.url)
    start = page.find("电子课本目录")
    end = page.find('<div class="sublist">', start if start >= 0 else 0)
    body = page[start:end if end >= 0 else len(page)]

    units: list[dict] = []
    current: dict | None = None
    seen_in_current_unit: set[str] = set()

    for href, label_html in ANCHOR_RE.findall(body):
        label = clean_text(label_html)
        if not label or label in seen_in_current_unit:
            continue
        if label.startswith(("关注公众号", "下一章", "上一章")):
            continue
        is_heading = bool(re.search(r"<b[^>]*>", label_html, re.IGNORECASE))
        heading_like = bool(
            re.match(
                r"^(第[一二三四五六七八九十百零〇]+[章单元课]|"
                r"UNIT\s+\d+|"
                r"序言|课题研究|学生实验|后记|附录)",
                label,
                re.IGNORECASE,
            )
        )

        if is_heading or heading_like or current is None:
            current = {"title": label, "sections": []}
            units.append(current)
            seen_in_current_unit = {label}
        else:
            current["sections"].append(label)
            seen_in_current_unit.add(label)

    return {
        "subject": source.subject,
        "publisher": source.publisher,
        "title": source.title,
        "sourceUrl": source.url,
        "units": units,
    }


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    output = root / "app" / "src" / "main" / "assets" / "knowledge_tree.json"
    built = []
    failures = []
    for index, source in enumerate(BOOKS, 1):
        try:
            parsed = parse_book(source)
            if not parsed["units"]:
                raise ValueError("no units found")
            built.append(parsed)
            print(f"[{index:02}/{len(BOOKS)}] {source.subject} {source.title}: {len(parsed['units'])} units")
        except Exception as exc:  # noqa: BLE001 - report every source failure and continue
            failures.append((source, str(exc)))
            print(f"FAILED {source.subject} {source.title}: {exc}", file=sys.stderr)

    payload = {
        "schemaVersion": 1,
        "catalogVersion": "2026-10-21",
        "source": "电子课本网公开目录，内置离线使用",
        "books": built,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {output} with {len(built)} books")
    if failures:
        print(f"{len(failures)} book(s) failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
