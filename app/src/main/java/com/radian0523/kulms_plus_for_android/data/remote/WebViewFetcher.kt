package com.radian0523.kulms_plus_for_android.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** セッション切れを示す例外。fetch 中にログアウトを検知した場合に送出する。 */
class SessionExpiredException : Exception("Session expired")

/** 認証情報ログインの結果。 */
sealed class LoginResult {
    /** ログイン成功（lms.gakusei のセッション確立）。 */
    object Success : LoginResult()
    /** 認証情報は受理されたが OTP（2FA）入力が必要。 */
    object OtpRequired : LoginResult()
    /** ID/パスワード誤りなどでログイン失敗。 */
    data class Failed(val message: String) : LoginResult()
}

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
    const val LOGIN_PORTAL_URL = "$BASE_URL/portal/login"
    const val IIMC_HOST = "auth.iimc.kyoto-u.ac.jp"

    lateinit var webView: WebView
        private set

    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRequests = ConcurrentHashMap<String, Continuation<String>>()

    /** WebView のローディング状態。WebViewClient コールバックで更新。 */
    private var isLoading = false

    /** ログイン進行中の URL 監視用 WebViewClient のインスタンス。 */
    private val loginNavigationListeners = mutableListOf<(String) -> Unit>()

    fun init(context: Context) {
        if (initialized) return
        webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(JsBridge, "Android")
            webViewClient = LoginAwareWebViewClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        initialized = true
    }

    /**
     * Fetch data from a Sakai API endpoint via JavaScript fetch().
     * 事前に ensureOnLMS() で lms.gakusei 上にいることを確認し、
     * 相対 URL で fetch する（SAML SSO 直後の origin 不一致を救済）。
     */
    suspend fun fetch(path: String): String {
        // WebView が lms.gakusei 上にいることを保証
        ensureOnLMS()

        // 進行中のナビゲーション（SAML リダイレクト等）が落ち着くまで待機
        waitForStableNavigation(maxSeconds = 15.0)

        val requestId = UUID.randomUUID().toString()
        // 相対 URL（カレント origin = lms.gakusei に対して解決される）
        val escaped = path.replace("'", "\\'")

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                pendingRequests[requestId] = cont
                cont.invokeOnCancellation { pendingRequests.remove(requestId) }

                val js = """
                    (async function() {
                        try {
                            const r = await fetch('$escaped', {credentials:'include', cache:'no-store'});
                            if (r.redirected && /\/portal\/(x?login|relogin|logout)/.test(r.url)) {
                                throw new Error('SESSION_EXPIRED');
                            }
                            var ct = r.headers.get('content-type') || '';
                            if (ct && ct.indexOf('json') === -1) {
                                throw new Error('SESSION_EXPIRED');
                            }
                            if (!r.ok) throw new Error('HTTP ' + r.status);
                            const text = await r.text();
                            Android.onResult('$requestId', text);
                        } catch(e) {
                            Android.onError('$requestId', 'FETCH_FAILED: ' + (e.message || 'Unknown error') + ' @ ' + window.location.href);
                        }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(js, null)
            }
        }
    }

    /**
     * ECS-ID/パスワードを使って KULMS にログインする。
     *
     * フロー:
     * 1. /portal/login へアクセス → IIMC SAML SSO へリダイレクト
     * 2. login.cgi が表示されたら DOM に値を注入してフォーム送信
     * 3. 以下のいずれかが起きるまで URL を監視:
     *    - lms.gakusei に戻る → Success
     *    - login.cgi にエラーメッセージが現れる → Failed
     *    - OTP 入力欄が表示される → OtpRequired
     */
    suspend fun loginWithCredentials(username: String, password: String): LoginResult {
        val result = withContext(Dispatchers.Main) {
            // 既存 cookie を消すと既存セッションが壊れるため、ここでは消さない
            // （セッション切れの場合は別途 clearData() を呼ぶ）

            val deferred = CompletableDeferred<LoginResult>()
            var credentialsInjected = false

            val listener: (String) -> Unit = listener@{ url ->
                if (deferred.isCompleted) return@listener
                Log.d(TAG, "loginWithCredentials: navigated to $url")

                // 成功: lms.gakusei の portal 系ページに戻った
                // 中継 URL（/Shibboleth.sso/* など）は除外
                val isPortalPage = url.startsWith("$BASE_URL/portal")
                    && !url.contains("/login")
                    && !url.contains("/relogin")
                    && !url.contains("/logout")
                if (isPortalPage) {
                    Log.d(TAG, "loginWithCredentials: portal reached, waiting for stable state")
                    deferred.complete(LoginResult.Success)
                    return@listener
                }

                // 2段階認証 (authselect.php / u2flogin.cgi / otplogin.cgi / motplogin.cgi)
                // → ECS-ID/パスワードは通ったが追加認証が必要 → WebView UI に切替
                val twoFactorPaths = listOf("/authselect.php", "/u2flogin.cgi",
                                             "/otplogin.cgi", "/motplogin.cgi")
                if (url.contains(IIMC_HOST) && twoFactorPaths.any { url.contains(it) }) {
                    Log.d(TAG, "loginWithCredentials: 2FA required → WebView fallback")
                    deferred.complete(LoginResult.OtpRequired)
                    return@listener
                }

                // login.cgi (ID/パスワード入力画面)
                // ※ "/login.cgi" でマッチさせる（u2flogin.cgi 等の誤マッチ回避）
                if (url.contains(IIMC_HOST) && url.contains("/login.cgi")) {
                    if (!credentialsInjected) {
                        credentialsInjected = true
                        injectCredentials(username, password)
                    } else {
                        // 2回目以降の login.cgi の判定:
                        // - error メッセージあり → 即座に failed
                        // - OTP 要素あり → otpRequired
                        // - unknown → 次の navigation (authselect.php 等) を待つ
                        //   ※ login.cgi (back なし) は認証成功後の中継ページである場合があるため、
                        //     ここで failed と判定すると authselect 遷移より早く終わってしまう
                        checkLoginCgiState { state ->
                            if (deferred.isCompleted) return@checkLoginCgiState
                            when (state) {
                                is CgiState.Otp -> {
                                    Log.d(TAG, "loginWithCredentials: OTP required")
                                    deferred.complete(LoginResult.OtpRequired)
                                }
                                is CgiState.Error -> {
                                    Log.d(TAG, "loginWithCredentials: failed - ${state.message}")
                                    deferred.complete(LoginResult.Failed(state.message))
                                }
                                is CgiState.Unknown -> {
                                    Log.d(TAG, "loginWithCredentials: login.cgi unknown state, waiting for next navigation")
                                    // 完了させず、次の navigation を待つ。
                                    // 全体タイムアウト (30秒) で最終的にはタイムアウト失敗。
                                }
                            }
                        }
                    }
                }
            }

            loginNavigationListeners.add(listener)
            try {
                webView.loadUrl(LOGIN_PORTAL_URL)
                // 全体タイムアウト 30 秒
                val r = withTimeoutOrNull(30_000L) { deferred.await() }
                    ?: LoginResult.Failed("ログイン処理がタイムアウトしました。ネットワーク状況を確認してください。")
                r
            } finally {
                loginNavigationListeners.remove(listener)
            }
        }

        // ログイン成功後、ページが完全に安定するまで待機（追加リダイレクトを吸収）
        if (result == LoginResult.Success) {
            waitForStableNavigation()
            Log.d(TAG, "loginWithCredentials: SUCCESS (stable)")
        }

        return result
    }

    /** login.cgi のフォームに認証情報を注入して送信する。 */
    private fun injectCredentials(username: String, password: String) {
        val u = username.replace("\\", "\\\\").replace("'", "\\'")
        val p = password.replace("\\", "\\\\").replace("'", "\\'")
        val js = """
            (function() {
                try {
                    var u = document.getElementById('username_input');
                    var p = document.getElementById('password_input');
                    var f = document.getElementById('login');
                    if (u && p && f) {
                        u.value = '$u';
                        p.value = '$p';
                        // input イベントを発火（ボタン活性化等のため）
                        u.dispatchEvent(new Event('input', {bubbles: true}));
                        p.dispatchEvent(new Event('input', {bubbles: true}));
                        f.submit();
                    }
                } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private sealed class CgiState {
        object Unknown : CgiState()
        data class Error(val message: String) : CgiState()
        object Otp : CgiState()
    }

    /** 現在の login.cgi ページの状態を判定する。 */
    private fun checkLoginCgiState(callback: (CgiState) -> Unit) {
        // OTP 入力欄が visible か、エラーメッセージがあるかを確認
        val js = """
            (function() {
                try {
                    var otpSend = document.getElementById('otp_send_button');
                    var dusername = document.getElementById('dusername_area');
                    var commentEl = document.getElementById('comment');

                    var otpVisible = false;
                    if (otpSend && otpSend.style.display !== 'none') otpVisible = true;
                    if (dusername && dusername.children.length > 0) otpVisible = true;

                    if (otpVisible) return JSON.stringify({type: 'otp'});

                    var msg = '';
                    if (commentEl) {
                        var t = (commentEl.innerText || commentEl.textContent || '').trim();
                        if (t && t.length > 1) msg = t;
                    }
                    if (msg) return JSON.stringify({type: 'error', message: msg});

                    return JSON.stringify({type: 'unknown'});
                } catch (e) {
                    return JSON.stringify({type: 'unknown'});
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { raw ->
            val state = try {
                // raw は JSON 文字列をさらに JSON エンコードした形 ("\"{...}\"")
                val unquoted = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
                when {
                    unquoted.contains("\"type\":\"otp\"") -> CgiState.Otp
                    unquoted.contains("\"type\":\"error\"") -> {
                        val msgRegex = "\"message\":\"([^\"]*)\"".toRegex()
                        val m = msgRegex.find(unquoted)?.groupValues?.getOrNull(1) ?: "ログインに失敗しました"
                        CgiState.Error(m)
                    }
                    else -> CgiState.Unknown
                }
            } catch (e: Exception) {
                CgiState.Unknown
            }
            callback(state)
        }
    }

    /**
     * IIMC ログイン画面に遷移して、現在 OTP モードかどうかを返す。
     * （OTP モードなら WebView を表示してユーザーに OTP 入力させる必要あり）
     */
    fun loadLoginPortal() {
        mainHandler.post {
            webView.loadUrl(LOGIN_PORTAL_URL)
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

    // MARK: - Navigation helpers

    /**
     * WebView のナビゲーションが安定するまで待機する。
     * [isLoading] が連続して 500ms 間 false であれば安定とみなす。
     * SAML SSO のような連続リダイレクト中、isLoading は短時間 false になる隙間があるため、
     * 安定期間を確保することでリダイレクト途中での JS 実行を防ぐ。
     */
    private suspend fun waitForStableNavigation(maxSeconds: Double = 10.0) = withContext(Dispatchers.Main) {
        val stepMs = 100L
        val quietRequired = 5  // 連続 5 ステップ (= 500ms) 静止で安定とみなす
        val maxSteps = (maxSeconds * 10).toInt()

        var quietCount = 0
        for (step in 0 until maxSteps) {
            delay(stepMs)
            if (isLoading) {
                quietCount = 0
            } else {
                quietCount++
                if (quietCount >= quietRequired) return@withContext
            }
        }
    }

    /**
     * 現在の WebView がまだ lms.gakusei.kyoto-u.ac.jp の document を表示していなければ
     * /portal をロードする。SAML SSO 直後など origin が auth.iimc に残ってる場合の救済。
     * 未ログインで SAML へリダイレクトされた場合は [SessionExpiredException] を投げる。
     */
    private suspend fun ensureOnLMS() = withContext(Dispatchers.Main) {
        val currentUrl = webView.url
        if (currentUrl != null && currentUrl.startsWith(BASE_URL)) return@withContext

        Log.d(TAG, "ensureOnLMS: navigating to $BASE_URL/portal (current=$currentUrl)")
        webView.loadUrl("$BASE_URL/portal")
        waitForStableNavigation(maxSeconds = 15.0)

        val newUrl = webView.url
        if (newUrl == null || !newUrl.startsWith(BASE_URL)) {
            Log.d(TAG, "ensureOnLMS: session expired (redirected to $newUrl)")
            throw SessionExpiredException()
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
            val exception = if (error.contains("SESSION_EXPIRED")) SessionExpiredException() else Exception(error)
            pendingRequests.remove(requestId)?.resumeWithException(exception)
        }
    }

    /** 通常の WebViewClient + ローディング状態追跡 + ログイン中の URL 通知。 */
    private class LoginAwareWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            isLoading = true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            isLoading = false
            super.onPageFinished(view, url)
            if (url != null) {
                // 同期的なリスト変更の中で iterate するためコピー
                val listeners = ArrayList(loginNavigationListeners)
                for (l in listeners) l(url)
            }
        }
    }
}
