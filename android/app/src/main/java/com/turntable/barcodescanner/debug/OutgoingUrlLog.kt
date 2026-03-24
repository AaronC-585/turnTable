package com.turntable.barcodescanner.debug

import okhttp3.Interceptor

/**
 * URL logging is intentionally disabled. **Debug** builds log Redacted JSON responses to Logcat
 * (`TurnTableJson`) via [DebugJsonResponseInterceptor] on [com.turntable.barcodescanner.redacted.RedactedApiClient].
 */
object OutgoingUrlLog {

    @Suppress("UNUSED_PARAMETER")
    fun log(method: String, url: String) {
        // No-op: do not record request URLs (release privacy; debug uses JSON body logging only).
    }
}

/** OkHttp application interceptor: no-op (see [OutgoingUrlLog]). */
val OutgoingUrlInterceptor = Interceptor { chain ->
    chain.proceed(chain.request())
}
