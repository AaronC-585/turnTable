package com.turntable.barcodescanner.debug

import okhttp3.Interceptor

/**
 * Records outgoing request URLs to [AppEventLog] (SYSTEM). Does **not** log response bodies or headers.
 */
object OutgoingUrlLog {

    fun log(method: String, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        AppEventLog.log(AppEventLog.Category.SYSTEM, "$method $u")
    }
}

/** OkHttp application interceptor: log method + full URL before the call. */
val OutgoingUrlInterceptor = Interceptor { chain ->
    val req = chain.request()
    OutgoingUrlLog.log(req.method, req.url.toString())
    chain.proceed(req)
}
