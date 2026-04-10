package com.kulms.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kulms.android.store.AssignmentViewModel
import com.kulms.android.ui.assignments.AssignmentListScreen
import com.kulms.android.ui.login.LoginScreen
import com.kulms.android.ui.theme.KULMSTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            KULMSTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent(viewModel: AssignmentViewModel = viewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // Both screens coexist (like iOS ZStack).
    // LoginScreen keeps the WebView alive for API calls.
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoggedIn) {
            AssignmentListScreen(viewModel = viewModel)
        } else {
            LoginScreen(viewModel = viewModel)
        }
    }
}
