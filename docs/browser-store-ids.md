# Browser ↔ store identifiers (verification)

Use this table when updating `KnownBrowsers.kt` (Android) and `IosSecondaryBrowser.swift` (iOS).  
**Play:** `https://play.google.com/store/apps/details?id=<package>`  
**App Store:** `https://apps.apple.com/app/id<id>`

| Browser (label) | Android package | App Store id (iOS) | Notes |
|-------------------|-----------------|----------------------|--------|
| Google Chrome | `com.android.chrome` | 535886823 | |
| Firefox | `org.mozilla.firefox` | 989804926 | |
| Firefox Focus | `org.mozilla.focus` | 1055677337 | |
| Firefox Klar | `org.mozilla.klar` | 1073435754 | |
| Samsung Internet | `com.sec.android.app.sbrowser` | — | **No iOS App Store app** — Android-only for store fallback. |
| Microsoft Edge | `com.microsoft.emmx` | 1288723196 | |
| Opera | `com.opera.browser` | 1411869974 | iOS: unified “Opera” app (replaces legacy id `363729560`). |
| Opera Mini | `com.opera.mini.native` | 1411869974 | Same iOS app as Opera; Android remains separate Opera Mini. |
| Opera GX | `com.opera.gx` | 1559740799 | Replaces stale `1512455743`. |
| Brave | `com.brave.browser` | 1052879175 | |
| Vivaldi | `com.vivaldi.browser` | 1633234600 | Replaces stale `646330346`. |
| DuckDuckGo | `com.duckduckgo.mobile.android` | 663592361 | |
| UC Browser | `com.UCMobile.intl` | 1048518592 | Replaces stale `586447697`. |
| Tor Browser | `org.torproject.torbrowser` | 519296448 | iOS opens **Onion Browser** listing (Tor-capable; not identical APK to Android). |
| Arc Search | — | 6472513080 | iOS-only in our list. |
| Orion Browser | — | 1484498200 | iOS-only (Kagi). |

Last reviewed: 2026-03 (manual `apps.apple.com` / Play checks).
