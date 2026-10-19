#!/bin/sh
# Generate a signing keystore for the Android app and base64-encode it so it
# can be stored as a GitHub Actions secret (ANDROID_SIGNING_KEYSTORE).
#
# Usage: ./android/generate-signing-key.sh
set -e

KEYSTORE=android/app/keystore.jks

if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists at $KEYSTORE"
    echo "Remove it first if you want a fresh one."
    exit 1
fi

mkdir -p android/app

keytool -genkey -v \
    -keystore "$KEYSTORE" \
    -storepass android -alias android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android,OU=Dev,O=Dev,C=US" \
    -sigalg SHA256withRSA

echo ""
echo "Keystore created at $KEYSTORE"
echo ""
echo "To use it in CI, add the following secrets to your repository:"
echo "  Settings > Secrets and variables > Actions > New repository secret"
echo ""
echo "  Name:  ANDROID_SIGNING_KEYSTORE"
echo "  Value: $(base64 -w0 "$KEYSTORE")"
echo ""
echo "  Name:  ANDROID_SIGNING_KEYSTORE_PASSWORD"
echo "  Value: android"
echo ""
echo "  Name:  ANDROID_SIGNING_KEY_ALIAS"
echo "  Value: android"
echo ""
echo "  Name:  ANDROID_SIGNING_KEY_PASSWORD"
echo "  Value: android"