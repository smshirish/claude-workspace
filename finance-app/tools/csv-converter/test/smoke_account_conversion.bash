#!/usr/bin/env bash
# Smoke test for account_conversion.bash: two runs with an overlapping account.
# Requires the jar to be built: mvn package -DskipTests
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() { echo "[FAIL] $*"; exit 1; }
assert_contains()     { grep -qF -- "$2" "$1" || fail "$1 should contain: $2"; }
assert_not_contains() { ! grep -qF -- "$2" "$1" || fail "$1 should NOT contain: $2"; }

HEADER="bank,acc_no,accountType,amount_chf,account_currency,as_of_date"

cat > "$WORK/run1.csv" <<EOF
$HEADER
UBS,111111,SAVINGS,100,CHF,21-Jun-26
Credit Suisse,222222,SAVINGS,200,CHF,21-Jun-26
,,,300,CHF,
EOF

cat > "$WORK/run2.csv" <<EOF
$HEADER
Credit Suisse,222222,SAVINGS,250,CHF,22-Jun-26
Raiffeisen,333333,CHECKING,400,CHF,22-Jun-26
EOF

OUT="$WORK/out"

"$DIR/account_conversion.bash" "$WORK/run1.csv" "$DIR/sample/mapping.json" "$OUT" >/dev/null
RUN1="$OUT/account_anony_2026-06-21.csv"
[[ -f "$RUN1" ]] || fail "missing $RUN1"
assert_contains     "$RUN1" "UBS,ANON-0001"
assert_contains     "$RUN1" "Credit Suisse,ANON-0002"
assert_not_contains "$RUN1" "111111"
assert_not_contains "$RUN1" "222222"

"$DIR/account_conversion.bash" "$WORK/run2.csv" "$DIR/sample/mapping.json" "$OUT" >/dev/null
RUN2="$OUT/account_anony_2026-06-22.csv"
[[ -f "$RUN2" ]] || fail "missing $RUN2"
assert_contains     "$RUN2" "Credit Suisse,ANON-0002"
assert_contains     "$RUN2" "Raiffeisen,ANON-0003"
assert_not_contains "$RUN2" "333333"

[[ -f "$RUN1" ]] || fail "first run output should still exist"

MAP="$OUT/anonym_to_real_id.json"
assert_contains "$MAP" '"ANON-0001" : "111111"'
assert_contains "$MAP" '"ANON-0002" : "222222"'
assert_contains "$MAP" '"ANON-0003" : "333333"'

echo "[PASS] account_conversion smoke test"
