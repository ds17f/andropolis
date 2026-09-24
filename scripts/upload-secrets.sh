#!/usr/bin/env bash
# Upload GitHub repository secrets from .secrets/ (never committed).
#   source .secrets/secrets.txt            # load passwords, KEYSSTORE_PATH, etc.
#   scripts/upload-secrets.sh               # dry-run: print what would be set
#   scripts/upload-secrets.sh --apply       # write the secrets to GitHub
set -euo pipefail
cd "$(dirname "$0")/.."

SECRETS_DIR=".secrets"

# Source secrets.txt if KEYSSTORE_PASSWORD is not already in the environment.
if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
   source "$SECRETS_DIR/secrets.txt"
fi

# KEYSSTORE_PATH is a bare filename, relative to .secrets/.
ks_path="$SECRETS_DIR/${KEYSSTORE_PATH:-upload.jks}"
if [[ ! -f "$ks_path" ]]; then
   echo "error: keystore not found at $ks_path" >&2
   exit 1
fi

APPLY=0
while [[ $# -gt 0 ]]; do
   case "$1" in
      --apply) APPLY=1; shift ;;
        *) break ;;
   esac
done

set_secret() {
   local key="$1" val="$2"
   if [[ $APPLY -eq 1 ]]; then
      gh secret set "$key" <<< "$val"
      echo "set $key"
   else
      echo "would set $key (length: ${#val})"
   fi
}

# KEYSTORE_BASE64 is computed from the keystore file; it is never stored in .secrets/.
set_secret KEYSTORE_BASE64 "$(base64 -w0 "$ks_path")"
set_secret KEYSTORE_PASSWORD "$KEYSTORE_PASSWORD"
set_secret KEY_ALIAS         "$KEY_ALIAS"
set_secret KEY_PASSWORD       "$KEY_PASSWORD"

# Play service account JSON.
play_json="$SECRETS_DIR/play-sa.json"
if [[ -f "$play_json" ]]; then
   set_secret PLAY_SERVICE_ACCOUNT_JSON "$(cat "$play_json")"
else
   echo "PLAY_SERVICE_ACCOUNT_JSON: $play_json not found — skip"
fi

echo "Done. CI / Release workflow will use these secrets."
