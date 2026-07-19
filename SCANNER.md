# turnTable Scanner companion

Barcode scanning lives in a separate **turnTable Scanner** app. After a scan it resolves artist/album (when a Redacted API key is available) and opens turnTable via:

```text
turntable://search?barcode=…&artist=…&album=…
```

## Install order (Android)

1. Install **turnTable** first (`turnTable.<abi>.apk`). It owns the shared prefs ContentProvider for the Redacted API key.
2. Install **turnTable Scanner** (`turnTableScanner.<abi>.apk`) signed with the **same keystore** as turnTable (required for the signature-protected prefs provider).
3. Set the Redacted API key in turnTable **Settings**. The scanner reads it for resolve-on-scan.

Launch scanning from turnTable’s dock (**Scan**) or open the Scanner app / `turntablescanner://scan`.

## Install order (iOS)

1. Install **turnTable** and **turnTable Scanner** with the same Apple team (App Group `group.com.2ndlifetech.turntable`).
2. Set the API key in turnTable Settings (migrates automatically from the old `search_prefs` suite).
3. Home / History **Scan** opens the companion when installed.

## Local builds

```bash
# Both release APKs (same ABI filters / signing as CI)
npm run build:android -- -PABI_FILTERS=arm64-v8a

# Debug
npm run build:android:debug
```

Outputs:

- `android/app/build/outputs/apk/…/turnTable…apk`
- `android/scanner/build/outputs/apk/…/turnTableScanner…apk`

CI (`.github/workflows/compile.yml`) builds and attaches both Android APKs per ABI, plus simulator zips for both iOS targets.

## Signing note

If Scanner is sideloaded with a different signing key than turnTable, Android will refuse the shared prefs provider and resolve will fall back to barcode-only deep links.
