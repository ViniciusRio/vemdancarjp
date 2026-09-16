#!/usr/bin/env bash
# Hunt hardcoded colors/values in src/ that should use design tokens.
# Deterministic — no LLM needed. Run from repo root.
#
# Usage:
#   ui-ux/scripts/hunt-hardcoded.sh [path]   # default: src/
#   ui-ux/scripts/hunt-hardcoded.sh src/ > reports/raw-hardcoded-$(date +%F).txt

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="${1:-src/}"
PATTERN='green-[0-9]|gray-[0-9]|amber-[0-9]|red-[0-9]|blue-[0-9]|sand-[0-9]|#[0-9a-fA-F]{3,6}\b'

cd "$ROOT"

# Prefer ripgrep; fall back to grep -rn if absent.
if command -v rg >/dev/null 2>&1; then
  rg -n --no-heading "$PATTERN" "$TARGET" || echo "(no matches)"
else
  grep -rnE "$PATTERN" "$TARGET" 2>/dev/null || echo "(no matches)"
fi

echo
echo "# Done. Replace each hit with a token from ui-ux/tokens/core.json."
