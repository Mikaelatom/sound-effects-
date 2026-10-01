#!/bin/bash
# Double-click me (Mac) or run: bash SETUP-Mac.command   (Linux)
cd "$(dirname "$0")" || exit 1
echo
echo "  =========================================="
echo "    Unreal connector for Claude - setup"
echo "  =========================================="
echo

fail() {
  echo
  echo "Something went wrong. Take a screenshot of this window and show it to Claude."
  read -r -p "Press Enter to close..."
  exit 1
}

if ! command -v uv >/dev/null 2>&1; then
  echo "Installing uv, a small helper that runs the connector..."
  curl -LsSf https://astral.sh/uv/install.sh | sh || fail
  export PATH="$HOME/.local/bin:$PATH"
fi

echo "Installing the connector..."
uv tool install --force --reinstall "$PWD" || fail
BIN="$(uv tool dir --bin)" || fail

uv run --no-project --with "$PWD" python setup/configure.py "$BIN/unreal-mcp" "${1:-}" || fail
echo
read -r -p "Press Enter to close..."
