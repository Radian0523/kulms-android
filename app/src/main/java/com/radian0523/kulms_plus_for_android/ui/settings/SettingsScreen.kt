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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper
import com.radian0523.kulms_plus_for_android.store.AssignmentViewModel

private val PRESET_OFFSETS = listOf(
    Pair(R.string.offset_10m, 10),
    Pair(R.string.offset_30m, 30),
    Pair(R.string.offset_1h, 60),
    Pair(R.string.offset_3h, 180),
    Pair(R.string.offset_5h, 300),
    Pair(R.string.offset_12h, 720),
    Pair(R.string.offset_24h, 1440),
    Pair(R.string.offset_2d, 2880),
    Pair(R.string.offset_3d, 4320),
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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
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
            // Notifications section
            SectionHeader(stringResource(R.string.section_notifications))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.deadline_remind), modifier = Modifier.weight(1f))
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
                        stringResource(R.string.notification_timing),
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
                                label = { Text(NotificationHelper.formatOffsetLabel(offset, context)) },
                                trailingIcon = if (notificationOffsets.size > 1) {
                                    {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete),
                                            modifier = Modifier.size(InputChipDefaults.IconSize)
                                        )
                                    }
                                } else null
                            )
                        }
                        if (notificationOffsets.size < 5) {
                            AssistChip(
                                onClick = { showOffsetPicker = true },
                                label = { Text(stringResource(R.string.add)) },
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
                    title = { Text(stringResource(R.string.add_timing)) },
                    text = {
                        Column {
                            for ((labelRes, minutes) in available) {
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
                                    Text(stringResource(labelRes))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showOffsetPicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            // Info section
            SectionHeader(stringResource(R.string.section_info))
            if (lastRefreshed != null) {
                InfoRow(stringResource(R.string.last_updated), viewModel.lastRefreshedText.substringAfter(": ", ""))
            }
            InfoRow(stringResource(R.string.assignment_count), "${assignments.size}")
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
                    stringResource(R.string.send_feedback),
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
                    stringResource(R.string.homepage),
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
                Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout_confirm)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
