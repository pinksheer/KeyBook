package com.keybook.vault.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keybook.vault.BuildConfig
import com.keybook.vault.data.SecurityState
import com.keybook.vault.data.VaultData
import com.keybook.vault.ui.BiometricAuthenticator
import com.keybook.vault.ui.VaultViewModel
import com.keybook.vault.ui.components.FieldDivider
import com.keybook.vault.ui.components.GlassCard
import com.keybook.vault.ui.components.KeybookTextField
import com.keybook.vault.ui.components.ScreenTopBar
import com.keybook.vault.ui.components.VaultMark
import com.keybook.vault.ui.findFragmentActivity
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    security: SecurityState,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var showAutoLock by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var showDisableBiometric by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var biometricBusy by remember { mutableStateOf(false) }
    var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestoreName by remember { mutableStateOf("") }
    var restorePreview by remember { mutableStateOf<VaultData?>(null) }
    var restoring by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBackupBytes
        pendingBackupBytes = null
        if (uri == null || bytes == null) {
            viewModel.endExternalFlow()
            viewModel.message("已取消备份")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                        output.flush()
                    } ?: error("无法写入备份文件")
                }.isSuccess
            }
            viewModel.endExternalFlow()
            if (success) {
                viewModel.markBackupExported()
                viewModel.message("加密备份已导出")
            } else {
                viewModel.message("备份导出失败，请重试")
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            viewModel.endExternalFlow()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "keybook-backup.keybook"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        val output = java.io.ByteArrayOutputStream()
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > 64 * 1024 * 1024) error("备份文件过大")
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    } ?: error("无法读取备份文件")
                    name to bytes
                }
            }
            viewModel.endExternalFlow()
            result.onSuccess { (name, bytes) ->
                pendingRestoreBytes = bytes
                pendingRestoreName = name
                showRestorePassword = true
            }.onFailure {
                viewModel.message("读取备份失败：${it.message ?: "未知错误"}")
            }
        }
    }

    fun enableBiometric() {
        if (biometricBusy) return
        if (!security.biometricAvailable) {
            viewModel.message("请先在系统设置中录入指纹")
            return
        }
        if (activity == null) {
            viewModel.message("当前设备无法使用指纹解锁")
            return
        }
        biometricBusy = true
        val cipher = viewModel.prepareBiometricEnrollment().getOrElse {
            biometricBusy = false
            viewModel.message(viewModel.messageFor(it))
            return
        }
        BiometricAuthenticator(activity).authenticate(
            title = "启用指纹解锁",
            subtitle = "验证指纹以安全地封装保险箱密钥",
            negativeText = "取消",
            cipher = cipher,
            onSuccess = { authenticated ->
                scope.launch {
                    val result = viewModel.completeBiometricEnrollment(authenticated)
                    biometricBusy = false
                    if (result.isSuccess) {
                        viewModel.message("指纹解锁已启用")
                    } else {
                        viewModel.cancelBiometricEnrollment()
                        viewModel.message(viewModel.messageFor(result.exceptionOrNull()!!))
                    }
                }
            },
            onError = {
                biometricBusy = false
                viewModel.cancelBiometricEnrollment()
                viewModel.message(it)
            },
            onCancel = {
                biometricBusy = false
                viewModel.cancelBiometricEnrollment()
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "top") {
            ScreenTopBar(title = "设置", eyebrow = "KEYBOOK", onBack = onBack)
        }

        item(key = "security-label") {
            SectionHeading("安全")
        }

        item(key = "security") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column {
                    SettingRow(
                        icon = Icons.Rounded.Fingerprint,
                        tint = KeybookColors.Mint,
                        title = "指纹解锁",
                        subtitle = when {
                            !security.biometricAvailable -> "请先在系统设置中录入指纹"
                            security.biometricEnabled -> "已启用 · 使用 Android Keystore 保护密钥"
                            else -> "轻触指纹即可打开保险箱"
                        },
                        onClick = {
                            if (security.biometricEnabled) showDisableBiometric = true else enableBiometric()
                        },
                        trailing = {
                            Switch(
                                checked = security.biometricEnabled,
                                onCheckedChange = { checked ->
                                    if (checked && !security.biometricEnabled) {
                                        enableBiometric()
                                    } else if (!checked && security.biometricEnabled) {
                                        showDisableBiometric = true
                                    }
                                },
                                enabled = security.biometricAvailable && !biometricBusy,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = KeybookColors.Ink,
                                    checkedTrackColor = KeybookColors.Mint,
                                    uncheckedThumbColor = KeybookColors.TextFaint,
                                    uncheckedTrackColor = KeybookColors.SurfaceSoft,
                                    uncheckedBorderColor = KeybookColors.Outline,
                                ),
                            )
                        },
                    )
                    FieldDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = Icons.Rounded.ManageAccounts,
                        tint = KeybookColors.Blue,
                        title = "账户与密码",
                        subtitle = "打开系统「密码和账户」管理页面",
                        onClick = { openAccountDashboard(context, viewModel) },
                    )
                    FieldDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = Icons.Rounded.Timer,
                        tint = KeybookColors.Gold,
                        title = "自动锁定",
                        subtitle = "离开应用后自动清除内存中的密钥",
                        onClick = { showAutoLock = true },
                        value = autoLockLabel(security.autoLockSeconds),
                    )
                    FieldDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = Icons.Rounded.Key,
                        tint = KeybookColors.Blue,
                        title = "修改主密码",
                        subtitle = "重新封装保险箱密钥，不会重新加密全部条目",
                        onClick = { showChangePassword = true },
                    )
                    FieldDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        tint = KeybookColors.TextMuted,
                        title = "立即锁定",
                        subtitle = "清除当前会话并返回锁屏",
                        onClick = { viewModel.lockNow() },
                        showChevron = false,
                    )
                }
            }
        }

        item(key = "data-label") {
            SectionHeading("数据")
        }

        item(key = "data") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column {
                    SettingRow(
                        icon = Icons.Rounded.Backup,
                        tint = KeybookColors.Mint,
                        title = "创建加密备份",
                        subtitle = if (security.lastBackupAt > 0) {
                            "上次备份：${Formatters.relative(security.lastBackupAt)}"
                        } else {
                            "导出一个受独立密码保护的 .keybook 文件"
                        },
                        onClick = { showBackupPassword = true },
                    )
                    FieldDivider(Modifier.padding(horizontal = 16.dp))
                    SettingRow(
                        icon = Icons.Rounded.Restore,
                        tint = KeybookColors.Gold,
                        title = "恢复备份",
                        subtitle = "从加密备份恢复并覆盖当前条目",
                        onClick = {
                            viewModel.beginExternalFlow()
                            openBackupLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
        }

        item(key = "notice") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KeybookColors.Gold.copy(alpha = 0.05f))
                    .border(1.dp, KeybookColors.Gold.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = KeybookColors.Gold,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "备份密码独立于主密码。请将备份文件与备份密码分开保管，遗失备份密码将无法恢复任何内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = KeybookColors.TextMuted,
                    lineHeight = 18.sp,
                )
            }
        }

        item(key = "about-label") {
            SectionHeading("关于")
        }

        item(key = "about") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column {
                    SettingRow(
                        icon = Icons.Rounded.Shield,
                        tint = KeybookColors.Mint,
                        title = "加密与隐私",
                        subtitle = "AES-256-GCM · PBKDF2 210,000 次",
                        onClick = { showPrivacy = true },
                    )
                    FieldDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VaultMark(size = 36.dp, glow = false)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "密码本 Keybook",
                                style = MaterialTheme.typography.titleSmall,
                                color = KeybookColors.Text,
                            )
                            Text(
                                "版本 ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = KeybookColors.TextFaint,
                            )
                        }
                        Text(
                            "本地优先",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeybookColors.MintDim,
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "没有账户，没有云端，没有后门。",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = KeybookColors.TextFaint,
                )
            }
        }
    }

    if (showAutoLock) {
        AutoLockDialog(
            selected = security.autoLockSeconds,
            onSelect = {
                viewModel.setAutoLockSeconds(it)
                showAutoLock = false
            },
            onDismiss = { showAutoLock = false },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            viewModel = viewModel,
            onDismiss = { showChangePassword = false },
            onSuccess = {
                showChangePassword = false
                viewModel.message("主密码已更新")
            },
        )
    }

    if (showBackupPassword) {
        BackupPasswordDialog(
            viewModel = viewModel,
            onDismiss = { showBackupPassword = false },
            onReady = { bytes ->
                showBackupPassword = false
                pendingBackupBytes = bytes
                viewModel.beginExternalFlow()
                createBackupLauncher.launch(Formatters.backupFilename())
            },
        )
    }

    if (showRestorePassword && pendingRestoreBytes != null) {
        RestorePasswordDialog(
            bytes = pendingRestoreBytes!!,
            filename = pendingRestoreName,
            viewModel = viewModel,
            onDismiss = {
                showRestorePassword = false
                pendingRestoreBytes = null
            },
            onPreview = { data ->
                showRestorePassword = false
                pendingRestoreBytes = null
                restorePreview = data
            },
        )
    }

    val preview = restorePreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { if (!restoring) restorePreview = null },
            containerColor = KeybookColors.SurfaceRaised,
            titleContentColor = KeybookColors.Text,
            textContentColor = KeybookColors.TextMuted,
            title = { Text("恢复这个备份？", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "备份包含 ${preview.items.size} 个条目。恢复后将覆盖当前保险箱中的全部内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KeybookColors.Danger.copy(alpha = 0.07f))
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = KeybookColors.Danger,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "此操作无法撤销，也不会自动备份当前数据。",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeybookColors.Danger,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoring = true
                        scope.launch {
                            val result = viewModel.applyRestore(preview)
                            restoring = false
                            if (result.isSuccess) {
                                restorePreview = null
                                viewModel.message("备份已恢复")
                            } else {
                                viewModel.message(viewModel.messageFor(result.exceptionOrNull()!!))
                            }
                        }
                    },
                ) { Text("覆盖并恢复", color = KeybookColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { restorePreview = null }, enabled = !restoring) {
                    Text("取消", color = KeybookColors.TextMuted)
                }
            },
        )
    }

    if (showDisableBiometric) {
        AlertDialog(
            onDismissRequest = { showDisableBiometric = false },
            containerColor = KeybookColors.SurfaceRaised,
            titleContentColor = KeybookColors.Text,
            textContentColor = KeybookColors.TextMuted,
            title = { Text("关闭指纹解锁？", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "关闭后需要使用主密码才能打开保险箱。已封装的密钥与 Keystore 条目会被删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disableBiometrics()
                    showDisableBiometric = false
                    viewModel.message("指纹解锁已关闭")
                }) { Text("关闭", color = KeybookColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDisableBiometric = false }) {
                    Text("取消", color = KeybookColors.TextMuted)
                }
            },
        )
    }

    if (showPrivacy) {
        PrivacyDialog(onDismiss = { showPrivacy = false })
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = KeybookColors.TextFaint,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
    )
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    value: String? = null,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KeybookColors.Text)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = KeybookColors.TextMuted,
                lineHeight = 17.sp,
            )
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = KeybookColors.Mint,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                color = KeybookColors.TextFaint,
            )
        }
    }
}

@Composable
private fun AutoLockDialog(selected: Long, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        0L to "立即",
        15L to "15 秒",
        60L to "1 分钟",
        300L to "5 分钟",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KeybookColors.SurfaceRaised,
        titleContentColor = KeybookColors.Text,
        textContentColor = KeybookColors.TextMuted,
        title = { Text("自动锁定", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "应用离开前台多久后锁定保险箱。锁定会清除内存中的密钥与明文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = KeybookColors.TextMuted,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(10.dp))
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = KeybookColors.Mint,
                                unselectedColor = KeybookColors.TextFaint,
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = KeybookColors.Text)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = KeybookColors.TextMuted) }
        },
    )
}

@Composable
private fun ChangePasswordDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogSurface {
            Text("修改主密码", style = MaterialTheme.typography.headlineMedium, color = KeybookColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                "所有条目使用同一个随机保险箱密钥加密，修改主密码只会重新封装该密钥。",
                style = MaterialTheme.typography.bodySmall,
                color = KeybookColors.TextMuted,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            KeybookTextField(
                value = current,
                onValueChange = { current = it; error = null },
                label = "当前主密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            KeybookTextField(
                value = newPassword,
                onValueChange = { newPassword = it; error = null },
                label = "新主密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            KeybookTextField(
                value = confirmation,
                onValueChange = { confirmation = it; error = null },
                label = "再次输入新密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                InlineError(error!!)
            }
            Spacer(Modifier.height(18.dp))
            DialogActions(
                confirmText = "更新主密码",
                busy = busy,
                confirmEnabled = current.isNotBlank() && newPassword.isNotBlank() && confirmation.isNotBlank(),
                onConfirm = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val result = viewModel.changePassword(current, newPassword, confirmation)
                            if (result.isSuccess) {
                                onSuccess()
                            } else {
                                error = viewModel.messageFor(result.exceptionOrNull()!!)
                                busy = false
                            }
                        }
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun BackupPasswordDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onReady: (ByteArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogSurface {
            Text("加密备份", style = MaterialTheme.typography.headlineMedium, color = KeybookColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                "设置一个独立的备份密码。备份文件使用 AES-256-GCM 加密，只有这个密码可以打开。",
                style = MaterialTheme.typography.bodySmall,
                color = KeybookColors.TextMuted,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            KeybookTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "备份密码",
                placeholder = "至少 8 位",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(10.dp))
            KeybookTextField(
                value = confirmation,
                onValueChange = { confirmation = it; error = null },
                label = "再次输入备份密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                InlineError(error!!)
            }
            Spacer(Modifier.height(18.dp))
            DialogActions(
                confirmText = "生成备份",
                busy = busy,
                confirmEnabled = password.isNotBlank() && confirmation.isNotBlank(),
                onConfirm = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val result = viewModel.createBackup(password, confirmation)
                            if (result.isSuccess) {
                                onReady(result.getOrThrow())
                            } else {
                                error = viewModel.messageFor(result.exceptionOrNull()!!)
                                busy = false
                            }
                        }
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun RestorePasswordDialog(
    bytes: ByteArray,
    filename: String,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
    onPreview: (VaultData) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogSurface {
            Text("恢复备份", style = MaterialTheme.typography.headlineMedium, color = KeybookColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                filename,
                style = MaterialTheme.typography.labelSmall,
                color = KeybookColors.MintDim,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "输入创建备份时设置的密码。解密成功后，你可以先预览条目数量再决定是否覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = KeybookColors.TextMuted,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            KeybookTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "备份密码",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                InlineError(error!!)
            }
            Spacer(Modifier.height(18.dp))
            DialogActions(
                confirmText = "解密并预览",
                busy = busy,
                confirmEnabled = password.isNotBlank(),
                onConfirm = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val result = viewModel.inspectBackup(bytes, password)
                            if (result.isSuccess) {
                                onPreview(result.getOrThrow())
                            } else {
                                error = viewModel.messageFor(result.exceptionOrNull()!!)
                                busy = false
                            }
                        }
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogSurface {
            Text("加密与隐私", style = MaterialTheme.typography.headlineMedium, color = KeybookColors.Text)
            Spacer(Modifier.height(14.dp))
            PrivacyBullet("本地加密", "每个条目在写入磁盘前使用 AES-256-GCM 加密。主密码通过 PBKDF2-HMAC-SHA256（210,000 次迭代）派生密钥。")
            PrivacyBullet("随机保险箱密钥", "所有条目共享一个 256 位随机密钥。修改主密码时只重新封装该密钥，不重写整个保险箱。")
            PrivacyBullet("指纹解锁", "保险箱密钥由 Android Keystore 中的 AES 密钥封装，必须通过强生物识别才能解密；新增指纹会自动使其失效。")
            PrivacyBullet("零网络", "应用没有申请网络权限，没有账户系统，也不会将任何数据上传到服务器。")
            PrivacyBullet("系统隔离", "Android 自动备份被禁用；正式版禁止截屏与最近任务预览。复制到剪贴板的内容会在 60 秒后自动清除。")
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭", color = KeybookColors.Mint)
            }
        }
    }
}

@Composable
private fun PrivacyBullet(title: String, body: String) {
    Row(Modifier.padding(bottom = 11.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(KeybookColors.Mint),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KeybookColors.Text)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = KeybookColors.TextMuted,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun DialogSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(KeybookColors.InkDeep.copy(alpha = 0.72f))
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = KeybookColors.SurfaceRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, KeybookColors.Outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DialogActions(
    confirmText: String,
    busy: Boolean,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss, enabled = !busy) {
            Text("取消", color = KeybookColors.TextMuted)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (confirmEnabled && !busy) KeybookColors.Mint else KeybookColors.SurfaceSoft
                )
                .clickable(enabled = confirmEnabled && !busy, onClick = onConfirm)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                if (busy) "处理中…" else confirmText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (confirmEnabled && !busy) KeybookColors.Ink else KeybookColors.TextFaint,
            )
        }
    }
}

private fun autoLockLabel(seconds: Long): String = when (seconds) {
    0L -> "立即"
    15L -> "15 秒"
    60L -> "1 分钟"
    300L -> "5 分钟"
    else -> "${seconds / 60} 分钟"
}

private fun openAccountDashboard(
    context: android.content.Context,
    viewModel: VaultViewModel,
) {
    val candidates = listOf(
        Intent().setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$AccountDashboardActivity",
            )
        ),
        Intent(Settings.ACTION_CREDENTIAL_PROVIDER),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
    viewModel.message("无法打开系统账户设置")
}


