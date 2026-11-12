package com.keybook.vault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.keybook.vault.data.SecurityState
import com.keybook.vault.ui.BiometricAuthenticator
import com.keybook.vault.ui.VaultViewModel
import com.keybook.vault.ui.components.KeybookTextField
import com.keybook.vault.ui.components.PrimaryButton
import com.keybook.vault.ui.components.VaultMark
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.util.PasswordGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(viewModel: VaultViewModel) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val strength = PasswordGenerator.strength(password)

    LaunchedEffect(Unit) {
        delay(120)
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = viewModel.createVault(password, confirmation)
            if (result.isFailure) {
                error = viewModel.messageFor(result.exceptionOrNull()!!)
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VaultMark(size = 50.dp)
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        "KEYBOOK",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                        color = KeybookColors.Mint,
                    )
                    Text("密码本", style = MaterialTheme.typography.titleLarge, color = KeybookColors.Text)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "建立你的\n加密保险箱",
                style = MaterialTheme.typography.displaySmall,
                color = KeybookColors.Text,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "一处存放密码、动态验证码与通行密钥。\n所有内容只以密文保存在这台设备上。",
                style = MaterialTheme.typography.bodyLarge,
                color = KeybookColors.TextMuted,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(22.dp))

            KeybookTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (error != null) error = null
                },
                label = "设置主密码",
                placeholder = "至少 8 位，建议混合字母与数字",
                leadingIcon = Icons.Rounded.Lock,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (visible) "隐藏密码" else "显示密码",
                            tint = KeybookColors.TextFaint,
                        )
                    }
                },
            )
            AnimatedVisibility(password.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    StrengthMeter(strength)
                }
            }
            Spacer(Modifier.height(10.dp))
            KeybookTextField(
                value = confirmation,
                onValueChange = {
                    confirmation = it
                    if (error != null) error = null
                },
                label = "再次输入主密码",
                leadingIcon = Icons.Rounded.Shield,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (password.isNotBlank() && confirmation.isNotBlank()) {
                        focusManager.clearFocus()
                        submit()
                    }
                }),
            )
            Spacer(Modifier.height(12.dp))
            WarningNote()
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = KeybookColors.MintDim,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "AES-256-GCM · PBKDF2 · 完全离线",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                    color = KeybookColors.TextFaint,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, KeybookColors.Ink.copy(alpha = 0.97f), KeybookColors.Ink)
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 12.dp),
        ) {
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    InlineError(error.orEmpty())
                    Spacer(Modifier.height(10.dp))
                }
            }
            PrimaryButton(
                text = "创建加密保险箱",
                enabled = password.isNotBlank() && confirmation.isNotBlank(),
                loading = busy,
                onClick = { submit() },
            )
        }
    }
}

@Composable
fun LockScreen(
    viewModel: VaultViewModel,
    security: SecurityState,
    activity: FragmentActivity?,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRecoveryHelp by remember { mutableStateOf(false) }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    var promptVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(120)
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    fun unlockWithPassword() {
        if (busy || password.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val result = viewModel.unlock(password)
            if (result.isFailure) {
                error = viewModel.messageFor(result.exceptionOrNull()!!)
                busy = false
            }
        }
    }

    fun launchBiometric() {
        if (activity == null || promptVisible || busy) return
        val cipherResult = viewModel.prepareBiometricUnlock()
        val cipher = cipherResult.getOrElse {
            error = viewModel.messageFor(it)
            return
        }
        promptVisible = true
        BiometricAuthenticator(activity).authenticate(
            title = "指纹解锁",
            subtitle = "验证指纹以打开你的加密保险箱",
            negativeText = "使用主密码",
            cipher = cipher,
            onSuccess = { authenticatedCipher ->
                scope.launch {
                    busy = true
                    val result = viewModel.unlockWithBiometric(authenticatedCipher)
                    if (result.isFailure) {
                        error = viewModel.messageFor(result.exceptionOrNull()!!)
                        busy = false
                    }
                    promptVisible = false
                }
            },
            onError = {
                error = it
                promptVisible = false
            },
            onCancel = { promptVisible = false },
        )
    }

    LaunchedEffect(security.biometricEnabled, security.biometricAvailable) {
        if (!autoPrompted && security.biometricEnabled && security.biometricAvailable) {
            autoPrompted = true
            delay(420)
            launchBiometric()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultMark(size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "KEYBOOK",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = KeybookColors.Mint,
                )
                Text("密码本", style = MaterialTheme.typography.titleMedium, color = KeybookColors.Text)
            }
        }
        Spacer(Modifier.height(58.dp))
        Text("欢迎回来", style = MaterialTheme.typography.displaySmall, color = KeybookColors.Text)
        Spacer(Modifier.height(10.dp))
        Text(
            "保险箱已上锁。输入主密码，或使用指纹继续。",
            style = MaterialTheme.typography.bodyLarge,
            color = KeybookColors.TextMuted,
        )
        Spacer(Modifier.height(34.dp))
        KeybookTextField(
            value = password,
            onValueChange = {
                password = it
                if (error != null) error = null
            },
            label = "主密码",
            leadingIcon = Icons.Rounded.Lock,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { unlockWithPassword() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "隐藏密码" else "显示密码",
                        tint = KeybookColors.TextFaint,
                    )
                }
            },
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                InlineError(error.orEmpty())
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "解锁保险箱",
            enabled = password.isNotBlank(),
            loading = busy,
            onClick = { unlockWithPassword() },
        )
        if (security.biometricEnabled && security.biometricAvailable) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KeybookColors.Surface.copy(alpha = 0.55f))
                    .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(18.dp))
                    .clickable(enabled = !promptVisible && !busy) { launchBiometric() }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (promptVisible) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = KeybookColors.Mint,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = KeybookColors.Mint,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    if (promptVisible) "等待指纹验证…" else "轻触使用指纹解锁",
                    style = MaterialTheme.typography.labelLarge,
                    color = KeybookColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "忘记主密码？",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { showRecoveryHelp = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = KeybookColors.TextFaint,
            )
        }
        Spacer(Modifier.height(40.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecurityDot()
            Text(
                "AES-256 加密",
                style = MaterialTheme.typography.labelSmall,
                color = KeybookColors.TextFaint,
            )
            Spacer(Modifier.width(12.dp))
            SecurityDot()
            Text(
                "仅存于本机",
                style = MaterialTheme.typography.labelSmall,
                color = KeybookColors.TextFaint,
            )
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showRecoveryHelp) {
        AlertDialog(
            onDismissRequest = { showRecoveryHelp = false },
            containerColor = KeybookColors.SurfaceRaised,
            titleContentColor = KeybookColors.Text,
            textContentColor = KeybookColors.TextMuted,
            title = { Text("主密码无法找回", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "主密码只用于在本机解密保险箱，我们不会保存它，也没有任何服务器可以重置它。\n\n" +
                        "如果忘记主密码，只能清除应用数据重新开始。建议在设置中定期导出加密备份，并单独保管备份密码。",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showRecoveryHelp = false }) {
                    Text("我知道了", color = KeybookColors.Mint)
                }
            },
        )
    }
}

@Composable
private fun StrengthMeter(score: Int) {
    val colors = listOf(
        KeybookColors.Danger,
        KeybookColors.Danger,
        KeybookColors.Gold,
        KeybookColors.Mint,
        KeybookColors.MintStrong,
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { index ->
                val active = index < score.coerceAtLeast(1)
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (active) colors[score] else KeybookColors.OutlineSoft),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "密码强度 · ${PasswordGenerator.strengthLabel(score)}",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            color = KeybookColors.TextFaint,
        )
    }
}

@Composable
private fun WarningNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KeybookColors.Gold.copy(alpha = 0.06f))
            .border(1.dp, KeybookColors.Gold.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(KeybookColors.Gold.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = KeybookColors.Gold,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            "主密码是唯一的解密钥匙，无法找回。请务必牢记，或将它保存在安全的地方。",
            style = MaterialTheme.typography.bodySmall,
            color = KeybookColors.TextMuted,
            lineHeight = 18.sp,
        )
    }
}

@Composable
fun InlineError(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeybookColors.Danger.copy(alpha = 0.08f))
            .border(1.dp, KeybookColors.Danger.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "!",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = KeybookColors.Danger,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = KeybookColors.Danger,
        )
    }
}

@Composable
private fun SecurityDot() {
    Box(
        Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(KeybookColors.MintDim),
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VaultMark(size = 64.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                "正在打开保险箱",
                style = MaterialTheme.typography.bodyMedium,
                color = KeybookColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
