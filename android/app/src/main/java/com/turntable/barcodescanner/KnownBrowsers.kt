package com.turntable.barcodescanner

/**
 * Browsers known on Android and iOS. Used for the "Open links in" list.
 * If the user selects a browser that is not installed, opening a link
 * will open the Play Store (Android) page for that app.
 *
 * Play Store URL format: https://play.google.com/store/apps/details?id=<package>
 * App Store URL format (iOS reference): https://apps.apple.com/app/id<id>
 */
object KnownBrowsers {
    data class Browser(
        val name: String,
        val packageName: String,
        val playStoreUrl: String,
        val appStoreId: String? = null
    ) {
        val appStoreUrl: String?
            get() = appStoreId?.let { "https://apps.apple.com/app/id$it" }
    }

    private const val PLAY = "https://play.google.com/store/apps/details?id="

    val all: List<Browser> = listOf(
        Browser("Google Chrome", "com.android.chrome", PLAY + "com.android.chrome", appStoreId = "535886823"),
        Browser("Firefox", "org.mozilla.firefox", PLAY + "org.mozilla.firefox", appStoreId = "989804926"),
        Browser("Firefox Focus", "org.mozilla.focus", PLAY + "org.mozilla.focus", appStoreId = "1055677337"),
        Browser("Firefox Klar", "org.mozilla.klar", PLAY + "org.mozilla.klar", appStoreId = "1073435754"),
        Browser("Samsung Internet", "com.sec.android.app.sbrowser", PLAY + "com.sec.android.app.sbrowser", appStoreId = "492129185"),
        Browser("Microsoft Edge", "com.microsoft.emmx", PLAY + "com.microsoft.emmx", appStoreId = "1288723196"),
        Browser("Opera", "com.opera.browser", PLAY + "com.opera.browser", appStoreId = "363729560"),
        Browser("Opera Mini", "com.opera.mini.native", PLAY + "com.opera.mini.native", appStoreId = "363729560"),
        Browser("Opera GX", "com.opera.gx", PLAY + "com.opera.gx", appStoreId = "1512455743"),
        Browser("Brave", "com.brave.browser", PLAY + "com.brave.browser", appStoreId = "1052879175"),
        Browser("Vivaldi", "com.vivaldi.browser", PLAY + "com.vivaldi.browser", appStoreId = "646330346"),
        Browser("DuckDuckGo", "com.duckduckgo.mobile.android", PLAY + "com.duckduckgo.mobile.android", appStoreId = "663592361"),
        Browser("UC Browser", "com.UCMobile.intl", PLAY + "com.UCMobile.intl", appStoreId = "586447697"),
        Browser("Soul Browser", "com.soul.browser", PLAY + "com.soul.browser"),
        Browser("Kiwi Browser", "com.kiwibrowser.browser", PLAY + "com.kiwibrowser.browser"),
        Browser("Mozilla Firefox Beta", "org.mozilla.fenix", PLAY + "org.mozilla.fenix"),
        Browser("Mozilla Firefox Nightly", "org.mozilla.fenix_nightly", PLAY + "org.mozilla.fenix_nightly"),
        Browser("Tor Browser", "org.torproject.torbrowser", PLAY + "org.torproject.torbrowser", appStoreId = "988646948"),
        Browser("Via Browser", "mark.via.gp", PLAY + "mark.via.gp"),
        Browser("Cake Web Browser", "com.cake.browser", PLAY + "com.cake.browser"),
    )

    fun findByPackage(packageName: String?): Browser? =
        if (packageName.isNullOrBlank()) null else all.find { it.packageName == packageName }
}
