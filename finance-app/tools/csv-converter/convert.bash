#!/usr/bin/env bash

if [ -z "$3" ]; then
    echo "Usage: convert.bash <input.csv> <mapping.json> <output.csv>"
    echo
    echo "  input.csv    - Source CSV with any column names"
    echo "  mapping.json - Maps input column names to Finance App column names"
    echo "  output.csv   - Destination file ready for Finance App upload"
    echo
    echo "Example:"
    echo "  convert.bash sample/input.csv sample/mapping.json accounts.csv"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INPUT_CSV=sample/accounts.csv
MAPPING_JSON=sample/mapping.json
OUTPUT_CSV=sample/accounts_converted.csv

# Strip UTF-8 BOM from input if present, write to a temp file
CLEAN_INPUT="$INPUT_CSV"
if head -c 3 "$INPUT_CSV" | grep -q $'\xef\xbb\xbf'; then
    CLEAN_INPUT="$(mktemp --suffix=.csv)"
    sed '1s/^\xEF\xBB\xBF//' "$INPUT_CSV" > "$CLEAN_INPUT"
    trap 'rm -f "$CLEAN_INPUT"' EXIT
fi

java -jar "$SCRIPT_DIR/target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar" "$CLEAN_INPUT" "$MAPPING_JSON" "$OUTPUT_CSV"
