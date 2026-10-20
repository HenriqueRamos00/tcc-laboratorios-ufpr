#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
auth_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
target_dir="$auth_dir/secrets"
keystore="$target_dir/auth-signing.p12"

mkdir -p "$target_dir"
if [ -f "$keystore" ]; then
  echo "Keystore already exists: $keystore"
  exit 0
fi

password=${AUTH_KEY_STORE_PASSWORD:-change-me}
keytool -genkeypair \
  -alias auth-signing \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore "$keystore" \
  -storepass "$password" \
  -keypass "$password" \
  -validity 3650 \
  -dname "CN=auth-service, OU=TCC, O=Lactec, L=Curitiba, ST=PR, C=BR" \
  -noprompt

echo "Created $keystore"
