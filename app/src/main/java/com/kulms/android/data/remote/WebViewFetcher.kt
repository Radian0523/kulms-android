package com.kulms.android.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Singleton WebView used for both SSO login display and API calls.
 *
 * Architecture: All Sakai API calls go through JavaScript fetch() in the WebView's
 * page context. Standard HTTP clients (OkHttp/Retrofit) cannot authenticate with
 * Sakai's session cookies from the WebView's cookie store.
 */
@SuppressLint("SetJavaScriptEnabled", "StaticFieldLeak")
object WebViewFetcher {
    private const val TAG = "WebViewFetcher"
    const val BASE_URL = "https://lms.gakusei.kyoto-u.ac.jp"

    lateinit var webView: WebView
        private set

    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRequests = ConcurrentHashMap<String, Continuation<String>>()

    fun init(context: Context) {
        if (initialized) return
        webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(JsBridge, "Android")
            webViewClient = WebViewClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        initialized = true
    }

    /**
     * Fetch data from a Sakai API endpoint via JavaScript fetch().
     * Must be called after the WebView has loaded a page on the same origin.
     */
    suspend fun fetch(path: String): String {
        val requestId = UUID.randomUUID().toString()
        val fullUrl = BASE_URL + path
        val escaped = fullUrl.replace("'", "\\'")

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                pendingRequests[requestId] = cont
                cont.invokeOnCancellation { pendingRequests.remove(requestId) }

                val js = """
                    (async function() {
                        try {
                            const r = await fetch('$escaped', {credentials:'include', cache:'no-store'});
                            if (!r.ok) throw new Error('HTTP ' + r.status);
                            const text = await r.text();
                            Android.onResult('$requestId', text);
                        } catch(e) {
                            Android.onError('$requestId', e.message || 'Unknown error');
                        }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(js, null)
            }
        }
    }

    fun clearData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        if (initialized) {
            mainHandler.post {
                webView.clearCache(true)
                webView.clearHistory()
            }
        }
    }

    private object JsBridge {
        @JavascriptInterface
        fun onResult(requestId: String, data: String) {
            Log.d(TAG, "onResult: $requestId (${data.length} chars)")
            pendingRequests.remove(requestId)?.resume(data)
        }

        @JavascriptInterface
        fun onError(requestId: String, error: String) {
            Log.e(TAG, "onError: $requestId - $error")
            pendingRequests.remove(requestId)?.resumeWithException(Exception(error))
        }
    }
}
