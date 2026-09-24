#!/usr/bin/env bash
# Upload GitHub repository secrets from .secrets (sourced locally, never committed).
#   source .secrets
#   scripts/upload-secrets.sh            # dry-run: prints what would be set
#   scripts/upload-secrets.sh --apply    # actually write the secrets
set -euo pipefail
cd "$(dirname "$0")/.."

# Load from .secrets in the repo root if sourced, otherwise read it.
if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
   source "$(dirname "$0")/../.secrets"
fi

apply() {
   local key="$1" val="$2"
   if [[ "${1:0:7}" == "--apply" ]]; then
      gh secret set "$key" <<< "$val"
      echo "set $key"
   else
      echo "would set $key (value length: ${#val})"
   fi
}

# --apply is a flag; shift it off before reading variables.
APPLY=0
while [[ $# -gt 0 ]]; do
   case "$1" in
      --apply) APPLY=1; shift ;;
      *) break ;;
   esac
done

# KEYSTORE_BASE64 is computed from the keystore file, not stored in .secrets.
ks_path="$(dirname "$0")/../${KEYSSTORE_PATH:-upload.jks}"
if [[ -f "$ks_path" ]]; then
   keystore_b64="$(base64 -w0 "$ks_path")"
   if [[ $APPLY -eq 1 ]]; then
      gh secret set KEYSSTORE_BASE64 <<< "$keystore_b64"
      echo "set KEYSTORE_BASE64"
   else
      echo "would set KEYSTORE_BASE64 (length: ${#keystore_b64})"
   fi
fi

gh_secret() {
   [[ $APPLY -eq 1 ]] && gh secret set "$1" <<< "$2" || echo "would set $1 (length: ${#2})"
}

gh_secret KEYSSTORE_PASSWORD  "$KEYSTORE_PASSWORD"
gh_secret KEY_ALIAS           "$KEY_ALIAS"
gh_secret KEY_PASSWORD        "$KEY_PASSWORD"
if [[ -n "${PLAY_SERVICE_ACCOUNT_JSON:-}" ]]; then
   gh_secret PLAY_SERVICE_ACCOUNT_JSON "$PLAY_SERVICE_ACCOUNT_JSON"
else
   echo "PLAY_SERVICE_ACCOUNT_JSON not set — skip (create the service account first)"
fi

echo "Done. Push: git push origin main --tags (CI will pick up secrets automatically)."
