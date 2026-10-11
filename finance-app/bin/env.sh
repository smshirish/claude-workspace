# Sourced (not executed) by start.sh. Exports app.env, then the secrets file that lives
# next to the anonymisation mapping file (finance-app/local/, gitignored).
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

set -a
source "$APP_DIR/app.env"
LOCAL_DIR="$(cd "$APP_DIR" && mkdir -p "$(dirname "$MAPPING_FILE")" && cd "$(dirname "$MAPPING_FILE")" && pwd)"
SECRETS_FILE="$LOCAL_DIR/finance-app-secrets.env"
if [[ -f "$SECRETS_FILE" ]]; then
  source "$SECRETS_FILE"
else
  echo "[WARN] Secrets file not found: $SECRETS_FILE (expects ADMIN_PASSWORD=...)" >&2
fi
set +a
