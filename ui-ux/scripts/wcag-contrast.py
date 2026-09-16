#!/usr/bin/env python3
"""Compute WCAG 2.1 contrast ratios for semantic text/background token pairs.

Reads ui-ux/tokens/core.json, resolves {palette.x} references, and emits a
JSON report of every text token against every background token. Deterministic.

Usage:
    python3 ui-ux/scripts/wcag-contrast.py [core.json]
Defaults to ui-ux/tokens/core.json. Prints JSON to stdout.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_IN = ROOT / "ui-ux" / "tokens" / "core.json"

REF_RE = re.compile(r"\{([\w.-]+)\}")


def hex_to_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def relative_luminance(rgb: tuple[int, int, int]) -> float:
    def chan(c: int) -> float:
        s = c / 255
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = (chan(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(fg: str, bg: str) -> float:
    l1 = relative_luminance(hex_to_rgb(fg))
    l2 = relative_luminance(hex_to_rgb(bg))
    lighter, darker = max(l1, l2), min(l1, l2)
    return round((lighter + 0.05) / (darker + 0.05), 2)


def resolve(value: str, palette: dict[str, str]) -> str:
    def sub(m: re.Match[str]) -> str:
        path = m.group(1).split(".", 1)[1] if "." in m.group(1) else m.group(1)
        return palette.get(path, m.group(0))
    return REF_RE.sub(sub, value)


def collect_palette(data: dict) -> dict[str, str]:
    palette: dict[str, str] = {}
    for key, tok in data.get("palette", {}).items():
        if isinstance(tok, dict) and tok.get("$type") == "color":
            palette[key] = tok["$value"]
    return palette


def collect_colors(data: dict, palette: dict[str, str]) -> dict[str, dict[str, str]]:
    """Return {group: {name: hex}} for every color token (resolved)."""
    out: dict[str, dict[str, str]] = {}
    for group, tokens in data.items():
        if not isinstance(tokens, dict):
            continue
        for name, tok in tokens.items():
            if not isinstance(tok, dict) or tok.get("$type") != "color":
                continue
            out.setdefault(group, {})[name] = resolve(tok["$value"], palette)
    return out


def main() -> int:
    in_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_IN
    data = json.loads(in_path.read_text(encoding="utf-8"))
    palette = collect_palette(data)
    colors = collect_colors(data, palette)

    text_colors = colors.get("text", {})
    bg_colors = {**colors.get("bg", {}), **colors.get("accent", {}), **colors.get("feedback", {})}

    results: list[dict] = []
    for fg_name, fg_hex in text_colors.items():
        for bg_name, bg_hex in bg_colors.items():
            ratio = contrast(fg_hex, bg_hex)
            results.append({
                "fg": f"text.{fg_name}",
                "bg": bg_name,
                "ratio": ratio,
                "aa_normal": ratio >= 4.5,
                "aa_large": ratio >= 3.0,
                "aaa_normal": ratio >= 7.0,
                "aaa_large": ratio >= 4.5,
            })

    fails = [r for r in results if not r["aa_normal"]]
    report = {
        "total_pairs": len(results),
        "fails_aa_normal": len(fails),
        "fails": sorted(fails, key=lambda r: r["ratio"]),
        "all": sorted(results, key=lambda r: (-r["ratio"], r["fg"], r["bg"])),
    }
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
