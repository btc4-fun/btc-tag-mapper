# GitHub builds and owner signing

Repository: https://github.com/btc4-fun/btc-tag-mapper

Push/PR runs execute core tests, Android host tests, lint and a debug build. Debug APK and reports are retained seven days. They are not production-signed APKs.

The owner authorized reading the existing local SMS signing backup. These four Actions secrets are now configured at organization level in `btc4-fun`, with selected access restricted to `btc-tag-mapper` and `btc-tag-checker`; metadata and both repository grants were read back and verified:

- ANDROID_RELEASE_KEYSTORE_BASE64
- ANDROID_RELEASE_STORE_PASSWORD
- ANDROID_RELEASE_KEY_ALIAS
- ANDROID_RELEASE_KEY_PASSWORD

Settings: https://github.com/organizations/btc4-fun/settings/secrets/actions

GitHub does not return stored secret values. Never print them in logs, commit them, upload the keystore as an artifact or generate a replacement key for each build.

Then manually run `Android Mapper` on `main` with `signed_release=true`. The signed job runs only after tests pass, restores the key into runner temporary storage, requires all four secrets, builds a non-debuggable release APK, verifies signature/package/permissions/archive and uploads APK, SHA256SUMS and public certificate fingerprints for fourteen days. Missing secrets fail closed. There is no public release or deployment step.

Compare the resulting signing-certificate fingerprint with the existing SMS app's release certificate to prove key identity. The former local mapper debug APK has a different signing identity; do not uninstall a data-containing installation to bypass Android's update refusal without first preserving exports and explicitly accepting data loss.

Customer repository: https://github.com/btc4-fun/btc-tag-checker — reserved for the separately developed customer app; creating the repository does not mean that code or CI has been migrated there.
