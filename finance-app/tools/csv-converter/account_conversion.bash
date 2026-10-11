#!/usr/bin/env bash
# Converts a raw bank accounts CSV to the Finance App import format.
#
# Steps performed automatically:
#   1. Strip summary/totals rows (rows with an empty first column)
#   2. Run the csv-converter jar (column rename + date normalisation)
#   3. Anonymise account numbers → ANON-XXXX
#   4. Write anonymised CSV and anonym_to_real_id.json
#
# Usage:
#   account_conversion.bash [input.csv] [mapping.json] [output.csv]
#
# Defaults (relative to the script's directory):
#   input.csv   → sample/accounts.csv
#   mapping.json → sample/mapping.json
#   output.csv  → sample/accounts_converted.csv
#
# anonym_to_real_id.json is always written alongside output.csv.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INPUT_CSV="${1:-$SCRIPT_DIR/sample/accounts.csv}"
MAPPING_JSON="${2:-$SCRIPT_DIR/sample/mapping.json}"
OUTPUT_CSV="${3:-/tmp/accounts_converted.csv}"
MAPPING_OUT="$(dirname "$OUTPUT_CSV")/anonym_to_real_id.json"

# ── Validate inputs ───────────────────────────────────────────────────────────

if [[ ! -f "$INPUT_CSV" ]]; then
  echo "[ERROR] Input CSV not found: $INPUT_CSV"
  exit 1
fi

if [[ ! -f "$MAPPING_JSON" ]]; then
  echo "[ERROR] Mapping JSON not found: $MAPPING_JSON"
  exit 1
fi

# ── Step 1: filter summary rows ───────────────────────────────────────────────

CLEAN_CSV="$(mktemp --suffix=.csv)"
trap 'rm -f "$CLEAN_CSV"' EXIT

python3 - "$INPUT_CSV" "$CLEAN_CSV" <<'PYEOF'
import csv, sys

src, dst = sys.argv[1], sys.argv[2]
with open(src, newline='', encoding='utf-8-sig') as f:
    reader = csv.reader(f)
    header = next(reader)
    rows = [r for r in reader if r and r[0].strip()]

with open(dst, 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(header)
    writer.writerows(rows)

print(f"[filter]  {len(rows)} account rows (summary rows removed)")
PYEOF

# ── Step 2 & 3: convert + anonymise ──────────────────────────────────────────

"$SCRIPT_DIR/convert.bash" "$CLEAN_CSV" "$MAPPING_JSON" "$OUTPUT_CSV"

echo ""
echo "[done]    Converted CSV:          $OUTPUT_CSV"
echo "[done]    Anonymisation mapping:  $MAPPING_OUT  (gitignored — keep private)"
