#!/usr/bin/env python3
"""Convert ui-ux/tokens/core.json (DTCG) into src/styles/tokens.css.

Resolves {palette.x} references and emits CSS custom properties grouped by
token category. Deterministic — no LLM needed.

Usage:
    python3 ui-ux/scripts/tokens-to-css.py [core.json] [tokens.css]
Defaults to ui-ux/tokens/core.json -> src/styles/tokens.css.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
DEFAULT_IN = ROOT / "ui-ux" / "tokens" / "core.json"
DEFAULT_OUT = ROOT / "src" / "styles" / "tokens.css"

REF_RE = re.compile(r"\{([\w.-]+)\}")


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


def format_value(token: dict, palette: dict[str, str]) -> str:
    v = token["$value"]
    t = token.get("$type", "")
    if t in ("spacing", "sizing", "fontSize", "lineHeight", "borderRadius"):
        return f"{v}px"
    if t == "fontWeight":
        return str(v)
    if t == "color":
        return resolve(v, palette)
    return str(v)


def main() -> int:
    in_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_IN
    out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUT

    data = json.loads(in_path.read_text(encoding="utf-8"))
    palette = collect_palette(data)

    lines: list[str] = [":root {"]
    for group, tokens in data.items():
        if group == "palette":
            continue
        lines.append(f"  /* {group} */")
        for name, tok in tokens.items():
            if not isinstance(tok, dict) or "$value" not in tok:
                continue
            css_name = f"--{group}-{name}".replace("_", "-")
            lines.append(f"  {css_name}: {format_value(tok, palette)};")
        lines.append("")
    lines.append("}")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"OK: {in_path.relative_to(ROOT)} -> {out_path.relative_to(ROOT)} ({len(lines)} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
