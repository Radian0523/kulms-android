package com.radian0523.kulms_plus_for_android.ui.login

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.radian0523.kulms_plus_for_android.data.local.CredentialStore
import com.radian0523.kulms_plus_for_android.data.remote.LoginResult
import com.radian0523.kulms_plus_for_android.data.remote.WebViewFetcher
import com.radian0523.kulms_plus_for_android.store.AssignmentViewModel
import kotlinx.coroutines.launch

/**
 * ECS-ID/パスワードを入力する独自ログイン画面。
 * パスワード自動入力（iOS のパスワードアプリと同様の Android Autofill）に対応。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CredentialLoginScreen(
    viewModel: AssignmentViewModel,
    onRequireWebViewLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var savePassword by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val usernameAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Username, AutofillType.EmailAddress),
            onFill = { value -> username = value }
        )
    }
    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { value -> password = value }
        )
    }
    autofillTree += usernameAutofillNode
    autofillTree += passwordAutofillNode

    // 起動時に保存済み認証情報を読み込み、自動ログインを試みる
    LaunchedEffect(Unit) {
        val stored = CredentialStore.load(context)
        if (stored != null) {
            username = stored.first
            password = stored.second
            // 自動ログイン
            performLogin(
                context = context,
                viewModel = viewModel,
                username = stored.first,
                password = stored.second,
                savePassword = true,
                onProgress = { isSubmitting = it },
                onError = { errorText = it },
                onRequireWebViewLogin = onRequireWebViewLogin
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "KULMS+",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "京都大学 学習支援システム",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorText = null },
            label = { Text("ECS-ID / SPS-ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    usernameAutofillNode.boundingBox = coords.boundsInWindow()
                }
                .onFocusChanged { state ->
                    autofill?.run {
                        if (state.isFocused) requestAutofillForNode(usernameAutofillNode)
                        else cancelAutofillForNode(usernameAutofillNode)
                    }
                },
            enabled = !isSubmitting
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = null },
            label = { Text("パスワード") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                if (!isSubmitting && username.isNotBlank() && password.isNotBlank()) {
                    scope.launch {
                        performLogin(
                            context, viewModel, username, password, savePassword,
                            onProgress = { isSubmitting = it },
                            onError = { errorText = it },
                            onRequireWebViewLogin = onRequireWebViewLogin
                        )
                    }
                }
            }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "パスワードを隠す" else "パスワードを表示"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    passwordAutofillNode.boundingBox = coords.boundsInWindow()
                }
                .onFocusChanged { state ->
                    autofill?.run {
                        if (state.isFocused) requestAutofillForNode(passwordAutofillNode)
                        else cancelAutofillForNode(passwordAutofillNode)
                    }
                },
            enabled = !isSubmitting
        )
        Spacer(modifier = Modifier.height(12.dp))

        // パスワード保存 (端末暗号化)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = savePassword,
                        onCheckedChange = { savePassword = it },
                        enabled = !isSubmitting
                    )
                    Text(
                        "この端末にパスワードを保存（暗号化）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (errorText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                errorText!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                keyboard?.hide()
                scope.launch {
                    performLogin(
                        context, viewModel, username, password, savePassword,
                        onProgress = { isSubmitting = it },
                        onError = { errorText = it },
                        onRequireWebViewLogin = onRequireWebViewLogin
                    )
                }
            },
            enabled = !isSubmitting && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text("  ログイン中...")
            } else {
                Text("ログイン")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onRequireWebViewLogin, enabled = !isSubmitting) {
            Text("Web ブラウザでログイン")
        }
        Text(
            "（パスキー / 多要素認証を使う場合）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private suspend fun performLogin(
    context: Context,
    viewModel: AssignmentViewModel,
    username: String,
    password: String,
    savePassword: Boolean,
    onProgress: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onRequireWebViewLogin: () -> Unit
) {
    onProgress(true)
    onError(null)

    when (val result = WebViewFetcher.loginWithCredentials(username.trim(), password)) {
        is LoginResult.Success -> {
            if (savePassword) {
                CredentialStore.save(context, username.trim(), password)
            }
            viewModel.setLoggedIn(true)
            viewModel.fetchAll(forceRefresh = true)
            // onProgress は画面遷移で自然に解放されるが、念のため
            onProgress(false)
        }
        is LoginResult.OtpRequired -> {
            // 認証情報が正しい場合のみ保存（OTP 待ちは認証情報自体は通った）
            if (savePassword) {
                CredentialStore.save(context, username.trim(), password)
            }
            onProgress(false)
            onRequireWebViewLogin()
        }
        is LoginResult.Failed -> {
            onError(result.message)
            onProgress(false)
        }
    }
}
