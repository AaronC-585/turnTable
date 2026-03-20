package com.turntable.barcodescanner

/**
 * Browsers for the Android **Settings → Open secondary links in** list (`all`).
 *
 * [androidOnly] — Android-only; omitted from the iOS picker (see `IosSecondaryBrowsers.swift`).
 * Entries with [appStoreId] are also on the App Store (cross-platform in the iOS list).
 *
 * Play Store URL: https://play.google.com/store/apps/details?id=<package>
 * App Store URL (iOS): https://apps.apple.com/app/id<appStoreId>
 */
object KnownBrowsers {
    data class Browser(
        val name: String,
        val packageName: String,
        val playStoreUrl: String,
        val appStoreId: String? = null,
        /** True = not listed on iOS (no iOS app or not paired in the iOS app). */
        val androidOnly: Boolean = false,
    ) {
        val appStoreUrl: String?
            get() = appStoreId?.let { "https://apps.apple.com/app/id$it" }
    }

    private const val PLAY = "https://play.google.com/store/apps/details?id="

    /** Full list for Android settings (all installable Android browsers). See `docs/browser-store-ids.md`. */
    val all: List<Browser> = listOf(
        Browser("Google Chrome", "com.android.chrome", PLAY + "com.android.chrome", appStoreId = "535886823"),
        Browser("Firefox", "org.mozilla.firefox", PLAY + "org.mozilla.firefox", appStoreId = "989804926"),
        Browser("Firefox Focus", "org.mozilla.focus", PLAY + "org.mozilla.focus", appStoreId = "1055677337"),
        Browser("Firefox Klar", "org.mozilla.klar", PLAY + "org.mozilla.klar", appStoreId = "1073435754"),
        /** No Samsung Internet on iOS App Store — [appStoreId] null; iOS picker omits this entry. */
        Browser("Samsung Internet", "com.sec.android.app.sbrowser", PLAY + "com.sec.android.app.sbrowser", appStoreId = null),
        Browser("Microsoft Edge", "com.microsoft.emmx", PLAY + "com.microsoft.emmx", appStoreId = "1288723196"),
        Browser("Opera", "com.opera.browser", PLAY + "com.opera.browser", appStoreId = "1411869974"),
        Browser("Opera Mini", "com.opera.mini.native", PLAY + "com.opera.mini.native", appStoreId = "1411869974"),
        Browser("Opera GX", "com.opera.gx", PLAY + "com.opera.gx", appStoreId = "1559740799"),
        Browser("Brave", "com.brave.browser", PLAY + "com.brave.browser", appStoreId = "1052879175"),
        Browser("Vivaldi", "com.vivaldi.browser", PLAY + "com.vivaldi.browser", appStoreId = "1633234600"),
        Browser("DuckDuckGo", "com.duckduckgo.mobile.android", PLAY + "com.duckduckgo.mobile.android", appStoreId = "663592361"),
        Browser("UC Browser", "com.UCMobile.intl", PLAY + "com.UCMobile.intl", appStoreId = "1048518592"),
        Browser("Soul Browser", "com.soul.browser", PLAY + "com.soul.browser", androidOnly = true),
        Browser("Kiwi Browser", "com.kiwibrowser.browser", PLAY + "com.kiwibrowser.browser", androidOnly = true),
        Browser("Mozilla Firefox Beta", "org.mozilla.fenix", PLAY + "org.mozilla.fenix", androidOnly = true),
        Browser("Mozilla Firefox Nightly", "org.mozilla.fenix_nightly", PLAY + "org.mozilla.fenix_nightly", androidOnly = true),
        /** iOS fallback: Onion Browser (Tor-capable); see docs. */
        Browser("Tor Browser", "org.torproject.torbrowser", PLAY + "org.torproject.torbrowser", appStoreId = "519296448"),
        Browser("Via Browser", "mark.via.gp", PLAY + "mark.via.gp", androidOnly = true),
        Browser("Cake Web Browser", "com.cake.browser", PLAY + "com.cake.browser", androidOnly = true),
    )

    fun findByPackage(packageName: String?): Browser? =
        if (packageName.isNullOrBlank()) null else all.find { it.packageName == packageName }
}
