#!/usr/bin/env bash

if [ -z "$3" ]; then
    echo "Usage: convert.bash <input.csv> <mapping.json> <output-dir>"
    echo
    echo "  input.csv             - Source CSV with any column names"
    echo "  mapping.json          - Maps input column names to Finance App column names"
    echo "  output-dir            - Directory for the output files (created if missing)"
    echo
    echo "  Outputs:"
    echo "    account_anony_<asOfDate>.csv - Converted and anonymised CSV (asOfDate in YYYY-MM-DD)"
    echo "    anonym_to_real_id.json       - ANON-XXXX -> real account numbers; merged across runs (gitignored)"
    echo
    echo "Example:"
    echo "  convert.bash sample/accounts.csv sample/mapping.json out/"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAR="$SCRIPT_DIR/target/csv-converter-1.0-SNAPSHOT-jar-with-dependencies.jar"

INPUT_CSV="$1"
MAPPING_JSON="$2"
OUTPUT_DIR="$3"

if [ ! -f "$JAR" ]; then
    echo "[ERROR] Jar not found: $JAR"
    echo "        Build first: cd $SCRIPT_DIR && mvn package -DskipTests"
    exit 1
fi

# Strip UTF-8 BOM from input if present, write to a temp file
CLEAN_INPUT="$INPUT_CSV"
if head -c 3 "$INPUT_CSV" | grep -q $'\xef\xbb\xbf'; then
    CLEAN_INPUT="$(mktemp --suffix=.csv)"
    sed '1s/^\xEF\xBB\xBF//' "$INPUT_CSV" > "$CLEAN_INPUT"
    trap 'rm -f "$CLEAN_INPUT"' EXIT
fi

java -jar "$JAR" "$CLEAN_INPUT" "$MAPPING_JSON" "$OUTPUT_DIR"
