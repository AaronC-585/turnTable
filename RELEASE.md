# turnTable — release checklist (Android + iOS)

Every **GitHub release** should attach **both**:

- **Android:** `turnTable.release-<version>.apk` (release build)
- **iOS:** `turnTable-ios-<version>.ipa` (exported from Xcode)

`<version>` should match **`./gradlew printAppVersion`** (Android `versionName`) and the iOS **Marketing Version** in Xcode.

---

## 1. Android (any machine with JDK + Android SDK)

```bash
cd android
./gradlew assembleRelease printAppVersion
# APK: app/build/outputs/apk/release/turnTable.release-<version>.apk
```

**`CurrentVersion.json`** at the repo root is overwritten on every `:app` build (task `writeCurrentVersion`, wired to `preBuild`) with **`version`** (same as APK `versionName`), **`releasePageUrl`**, and **`assets.apk`** (default GitHub download URL pattern from `github_update.xml` owner/repo). The in-app update check reads this manifest from **raw.githubusercontent.com** (no GitHub releases API). After **`scripts/release-github.sh`**, the script rewrites **`CurrentVersion.json`** with the exact APK (and optional IPA) asset URLs.

Configure release signing via `keystore.properties` or env vars if needed; otherwise Gradle may sign with the debug keystore (with a warning).

---

## 2. iOS (macOS + Xcode only)

1. **Native library:** from repo root, `make ios` (or `./ios/scripts/build_lib.sh`). Requires Xcode / iOS SDK.
2. **Version:** In Xcode, select target **turnTable** → **General** → set **Version** (Marketing) and **Build** to align with the Android release string.
3. **Archive:** **Product → Archive** → **Distribute App** → choose **Ad Hoc**, **Development**, or **App Store Connect** as appropriate → export **IPA**.
4. Name the file for releases, e.g. `turnTable-ios-<version>.ipa`.

---

## 3. Publish on GitHub

**Option A — script (recommended):**

```bash
./scripts/release-github.sh v<version> <path-to.apk> <path-to.ipa>
```

**Option B — GitHub CLI:**

```bash
gh release create v<version> \
  android/app/build/outputs/apk/release/turnTable.release-<version>.apk \
  path/to/turnTable-ios-<version>.ipa \
  --title "turnTable <version>" \
  --notes-file CHANGELOG.md
```

**Option C:** Create the release in the GitHub UI and **Attach binaries** for both files.

After **Option A**, the script updates **`CurrentVersion.json`** (primary) and **`android/app/update-check-latest-version.txt`** (legacy plain-text fallback). **Commit and push** `CurrentVersion.json` on your default branch so raw GitHub matches the shipped release.

---

## One platform only (hotfix)

If a release only ships one platform, say so in the **release title** and **notes** (e.g. “Android-only hotfix”). The Cursor rule allows explicit single-platform releases when documented.
