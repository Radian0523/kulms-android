package com.radian0523.kulms_plus_for_android.ui.login

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.radian0523.kulms_plus_for_android.data.remote.WebViewFetcher
import com.radian0523.kulms_plus_for_android.store.AssignmentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ログイン画面のルート。
 * デフォルトでは独自 UI（CredentialLoginScreen）を表示。
 * 多要素認証が必要な場合や、ユーザーが明示的に選択した場合は WebView ログイン UI に切り替える。
 */
@Composable
fun LoginScreen(viewModel: AssignmentViewModel) {
    var useWebView by remember { mutableStateOf(false) }
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // セッション切れで再ログインに戻る場合は credential 入力画面に戻す
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            useWebView = false
        }
    }

    if (useWebView) {
        WebViewLoginPanel(
            viewModel = viewModel,
            onBack = { useWebView = false }
        )
    } else {
        CredentialLoginScreen(
            viewModel = viewModel,
            onRequireWebViewLogin = { useWebView = true }
        )
    }
}

/**
 * 従来の WebView ベースのログイン UI（多要素認証用フォールバック）。
 */
@Composable
private fun WebViewLoginPanel(
    viewModel: AssignmentViewModel,
    onBack: () -> Unit
) {
    var isVerifying by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { _ ->
                WebViewFetcher.webView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // 既に IIMC のページにいる可能性が高いので、URL は触らない
                    (parent as? ViewGroup)?.removeView(this)
                }
            },
            update = { _ -> }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (errorText != null) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (isVerifying) return@Button
                    isVerifying = true
                    errorText = null

                    scope.launch {
                        try {
                            val text = withContext(Dispatchers.Main) {
                                WebViewFetcher.fetch("/direct/site.json?_limit=1")
                            }

                            if (text.contains("site_collection") && text.length > 60) {
                                viewModel.setLoggedIn(true)
                                viewModel.fetchAll(forceRefresh = true)
                            } else {
                                errorText = "セッションが確認できません。ログインしてから再度タップしてください。"
                            }
                        } catch (e: Exception) {
                            errorText = "確認に失敗しました: ${e.localizedMessage}"
                        }
                        isVerifying = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("  認証を確認中...", color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("ログイン完了 → 課題一覧へ")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "認証完了後にタップしてください",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack, enabled = !isVerifying) {
                Text("ID/パスワード入力に戻る")
            }
        }
    }
}
