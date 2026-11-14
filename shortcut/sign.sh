#!/usr/bin/env bash
#
# Sign the exported iOS Shortcut for distribution.
#
#   ./shortcut/sign.sh [source] [output]
#
# Input  : shortcut/source/Send Token to TV.shortcut  (exported from the Shortcuts app)
# Output : shortcut/Send Token to TV.shortcut         (the shipped artifact)
#
# The file name is the shortcut's name on import, so keep it verbatim.
#
# `shortcuts sign -m anyone` wraps the workflow in an AEA1 container. That wrapper
# is what lets a phone import the file without an "untrusted shortcut" toggle; a
# bare export cannot be imported on iOS 15 and later. The signature gates import
# only — the TV address stays editable after import.
#
# The Mac must be signed in to iCloud for the signing step. If it reports an
# authentication error, check System Settings -> Apple Account (and iCloud Drive).
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
src=${1:-"$here/source/Send Token to TV.shortcut"}
out=${2:-"$here/Send Token to TV.shortcut"}

[ -f "$src" ] || { echo "error: no source at $src" >&2; exit 1; }
[ "$src" != "$out" ] || { echo "error: source and output are the same file" >&2; exit 1; }
case "$src" in
  *.shortcut) ;;
  *) echo "warning: source does not end in .shortcut" >&2 ;;
esac

# Sign to a temp file: a failed run must not leave an older artifact in place that
# the AEA1 check below would happily pass.
tmp="$out.tmp-$$"
trap 'rm -f "$tmp"' EXIT

shortcuts sign -m anyone -i "$src" -o "$tmp"

magic=$(xxd -p -l 4 "$tmp")
[ "$magic" = "41454131" ] || { echo "error: output is not an AEA1 container (magic '$magic')" >&2; exit 1; }

mv "$tmp" "$out"

echo "signed:        $out"
echo "source sha256: $(shasum -a 256 "$src" | cut -d' ' -f1)"
echo "signed sha256: $(shasum -a 256 "$out" | cut -d' ' -f1)"
