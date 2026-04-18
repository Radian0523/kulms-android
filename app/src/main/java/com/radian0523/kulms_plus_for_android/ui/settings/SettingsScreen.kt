package com.radian0523.kulms_plus_for_android.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper
import com.radian0523.kulms_plus_for_android.store.AssignmentViewModel

private val PRESET_OFFSETS = listOf(
    "10分前" to 10,
    "30分前" to 30,
    "1時間前" to 60,
    "3時間前" to 180,
    "5時間前" to 300,
    "12時間前" to 720,
    "24時間前" to 1440,
    "2日前" to 2880,
    "3日前" to 4320,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: AssignmentViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val assignments by viewModel.assignments.collectAsState()
    val lastRefreshed by viewModel.lastRefreshed.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showOffsetPicker by remember { mutableStateOf(false) }

    var autoComplete by remember { mutableStateOf(viewModel.autoComplete) }
    var notificationOffsets by remember {
        mutableStateOf(NotificationHelper.getNotificationOffsets(context))
    }

    val notificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    } else true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "閉じる")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Auto-complete section
            SectionHeader("課題更新")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("提出状態の自動判定", modifier = Modifier.weight(1f))
                Switch(
                    checked = autoComplete,
                    onCheckedChange = {
                        autoComplete = it
                        viewModel.setAutoComplete(it)
                    }
                )
            }
            Text(
                "OFFにすると手動チェックのみで完了判定\n※クイズ・テストは手動チェックのみ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
            )
            HorizontalDivider()

            // Notifications section
            SectionHeader("通知")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("締切リマインド", modifier = Modifier.weight(1f))
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val intent = Intent().apply {
                                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
            if (notificationsEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text(
                        "通知タイミング",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (offset in notificationOffsets.sortedDescending()) {
                            InputChip(
                                selected = false,
                                onClick = {
                                    if (notificationOffsets.size > 1) {
                                        val updated = notificationOffsets.filter { it != offset }
                                        NotificationHelper.setNotificationOffsets(context, updated)
                                        notificationOffsets = updated.sortedDescending()
                                        viewModel.rescheduleNotifications()
                                    }
                                },
                                label = { Text(NotificationHelper.formatOffsetLabel(offset)) },
                                trailingIcon = if (notificationOffsets.size > 1) {
                                    {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "削除",
                                            modifier = Modifier.size(InputChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                        if (notificationOffsets.size < 5) {
                            AssistChip(
                                onClick = { showOffsetPicker = true },
                                label = { Text("追加") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(InputChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider()

            // Offset picker dialog
            if (showOffsetPicker) {
                val available = PRESET_OFFSETS.filter { it.second !in notificationOffsets }
                AlertDialog(
                    onDismissRequest = { showOffsetPicker = false },
                    title = { Text("通知タイミングを追加") },
                    text = {
                        Column {
                            for ((label, minutes) in available) {
                                TextButton(
                                    onClick = {
                                        val updated = notificationOffsets + minutes
                                        NotificationHelper.setNotificationOffsets(context, updated)
                                        notificationOffsets = updated.sortedDescending()
                                        viewModel.rescheduleNotifications()
                                        showOffsetPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showOffsetPicker = false }) {
                            Text("キャンセル")
                        }
                    }
                )
            }

            // Info section
            SectionHeader("情報")
            if (lastRefreshed != null) {
                InfoRow("最終更新", viewModel.lastRefreshedText.removePrefix("最終更新: "))
            }
            InfoRow("課題数", "${assignments.size}")
            HorizontalDivider()

            // Feedback & Support
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLScLn4G2IF1w0-QOWPKZ7R1LXjOq7OocYUmGJLoNA6JBuA20EA/viewform")
                        )
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "ご意見・要望を送る",
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://radian0523.github.io/kulms-extension/")
                        )
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "ホームページ",
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()

            // Logout
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("ログアウト", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("ログアウトしますか？") },
            text = { Text("セッションとキャッシュが削除されます") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onDismiss()
                }) {
                    Text("ログアウト", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
