package com.keybook.vault.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultItem
import com.keybook.vault.data.label
import com.keybook.vault.security.PasskeyFactory
import com.keybook.vault.totp.Totp
import com.keybook.vault.ui.VaultViewModel
import com.keybook.vault.ui.components.CountdownRing
import com.keybook.vault.ui.components.GlassCard
import com.keybook.vault.ui.components.KeybookTextField
import com.keybook.vault.ui.components.PrimaryButton
import com.keybook.vault.ui.components.ScreenTopBar
import com.keybook.vault.ui.components.SheetHandle
import com.keybook.vault.ui.components.TypeGlyph
import com.keybook.vault.ui.components.rememberTotpState
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.ui.theme.KeybookTypography
import com.keybook.vault.util.PasswordGenerator
import com.keybook.vault.util.PasswordOptions
import com.keybook.vault.util.SensitiveClipboard
import kotlinx.coroutines.launch
import java.util.UUID

class EntryEditorViewModel(
    initial: VaultItem?,
    initialType: ItemType,
) : ViewModel() {
    val editing: Boolean = initial != null
    val type: ItemType = initial?.type ?: initialType
    val originalId: String = initial?.id ?: UUID.randomUUID().toString()
    val createdAt: Long = initial?.createdAt ?: System.currentTimeMillis()

    var title by mutableStateOf(initial?.title.orEmpty())
    var username by mutableStateOf(initial?.username.orEmpty())
    var url by mutableStateOf(initial?.url.orEmpty())
    var notes by mutableStateOf(initial?.notes.orEmpty())
    var favorite by mutableStateOf(initial?.favorite ?: false)

    var password by mutableStateOf(initial?.password.orEmpty())
    var totpSecret by mutableStateOf(initial?.totpSecret.orEmpty())
    var totpAlgorithm by mutableStateOf(initial?.totpAlgorithm ?: "SHA1")
    var totpDigits by mutableStateOf(initial?.totpDigits ?: 6)
    var totpPeriod by mutableStateOf(initial?.totpPeriod ?: 30)

    var rpId by mutableStateOf(initial?.rpId.orEmpty())
    var credentialId by mutableStateOf(initial?.credentialId.orEmpty())
    var privateKey by mutableStateOf(initial?.privateKey.orEmpty())
    var publicKey by mutableStateOf(initial?.publicKey.orEmpty())
    var userHandle by mutableStateOf(initial?.userHandle.orEmpty())
    var passkeyAlgorithm by mutableStateOf(initial?.passkeyAlgorithm ?: "ES256")

    fun validate(): String? {
        if (title.isBlank()) return "请填写标题"
        return when (type) {
            ItemType.PASSWORD -> if (password.isBlank()) "请填写密码" else null
            ItemType.TOTP -> if (!Totp.isValidSecret(totpSecret)) "请填写有效的 Base32 TOTP 密钥" else null
            ItemType.PASSKEY -> if (rpId.isBlank()) "请填写依赖方（RP ID）" else null
        }
    }

    fun build(): VaultItem {
        val now = System.currentTimeMillis()
        return VaultItem(
            id = originalId,
            type = type,
            title = title.trim(),
            username = username.trim(),
            password = if (type == ItemType.PASSWORD) password else "",
            url = url.trim(),
            notes = notes.trim(),
            totpSecret = if (type == ItemType.TOTP) totpSecret.trim() else "",
            totpAlgorithm = if (type == ItemType.TOTP) totpAlgorithm else "SHA1",
            totpDigits = if (type == ItemType.TOTP) totpDigits else 6,
            totpPeriod = if (type == ItemType.TOTP) totpPeriod else 30,
            rpId = if (type == ItemType.PASSKEY) rpId.trim() else "",
            credentialId = if (type == ItemType.PASSKEY) credentialId.trim() else "",
            privateKey = if (type == ItemType.PASSKEY) privateKey.trim() else "",
            publicKey = if (type == ItemType.PASSKEY) publicKey.trim() else "",
            userHandle = if (type == ItemType.PASSKEY) userHandle.trim() else "",
            passkeyAlgorithm = if (type == ItemType.PASSKEY) passkeyAlgorithm else "ES256",
            favorite = favorite,
            createdAt = createdAt,
            updatedAt = now,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    existing: VaultItem?,
    initialType: ItemType,
    vaultViewModel: VaultViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val factory = remember(existing?.id, initialType) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EntryEditorViewModel(existing, initialType) as T
            }
        }
    }
    val editor = viewModel<EntryEditorViewModel>(
        key = "editor-${existing?.id ?: "new-${initialType.name}"}",
        factory = factory,
    )
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var revealPassword by remember { mutableStateOf(false) }
    var revealTotp by remember { mutableStateOf(false) }
    var revealPrivateKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGenerator by remember { mutableStateOf(false) }
    var importedOtp by remember { mutableStateOf(false) }
    val generatorSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totpPreview = if (editor.type == ItemType.TOTP) {
        rememberTotpState(editor.totpSecret, editor.totpAlgorithm, editor.totpDigits, editor.totpPeriod)
    } else {
        null
    }

    fun save() {
        if (busy) return
        val validation = editor.validate()
        if (validation != null) {
            error = validation
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = vaultViewModel.upsert(editor.build())
            if (result.isFailure) {
                error = vaultViewModel.messageFor(result.exceptionOrNull()!!)
                busy = false
            } else {
                onSaved()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "top") {
            ScreenTopBar(
                title = if (editor.editing) "编辑${editor.type.label}" else "新建${editor.type.label}",
                eyebrow = "加密条目",
                onBack = onBack,
                actions = {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (editor.validate() == null) KeybookColors.Mint else KeybookColors.Surface
                            )
                            .border(1.dp, KeybookColors.OutlineSoft, CircleShape)
                            .clickable(enabled = !busy) { save() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "保存",
                            tint = if (editor.validate() == null) KeybookColors.Ink else KeybookColors.TextFaint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }

        item(key = "type") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(KeybookColors.Surface.copy(alpha = 0.55f))
                    .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeGlyph(editor.type, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        when (editor.type) {
                            ItemType.PASSWORD -> "登录密码"
                            ItemType.TOTP -> "基于时间的动态验证码"
                            ItemType.PASSKEY -> "WebAuthn 通行密钥"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = KeybookColors.Text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (editor.type) {
                            ItemType.PASSWORD -> "账户、密码与网站会一起加密保存"
                            ItemType.TOTP -> "兼容 SHA1 / SHA256 / SHA512 标准 TOTP"
                            ItemType.PASSKEY -> "生成并保存 ES256 密钥对与凭证信息"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = KeybookColors.TextMuted,
                    )
                }
            }
        }

        item(key = "title") {
            KeybookTextField(
                value = editor.title,
                onValueChange = {
                    editor.title = it
                    error = null
                },
                label = "标题",
                placeholder = when (editor.type) {
                    ItemType.PASSWORD -> "例如：GitHub"
                    ItemType.TOTP -> "例如：GitHub 2FA"
                    ItemType.PASSKEY -> "例如：GitHub Passkey"
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                isError = error == "请填写标题",
            )
        }

        item(key = "username") {
            KeybookTextField(
                value = editor.username,
                onValueChange = { editor.username = it },
                label = "账户 / 用户名",
                placeholder = "邮箱、用户名或手机号",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )
        }

        if (editor.type != ItemType.PASSKEY) {
            item(key = "url") {
                KeybookTextField(
                    value = editor.url,
                    onValueChange = { editor.url = it },
                    label = "网站（可选）",
                    placeholder = "https://example.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
            }
        }

        if (editor.type == ItemType.PASSWORD) {
            item(key = "password") {
                KeybookTextField(
                    value = editor.password,
                    onValueChange = {
                        editor.password = it
                        error = null
                    },
                    label = "密码",
                    placeholder = "输入或生成一个强密码",
                    leadingIcon = Icons.Rounded.Lock,
                    visualTransformation = if (revealPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    isError = error == "请填写密码",
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { revealPassword = !revealPassword },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (revealPassword) "隐藏密码" else "显示密码",
                                    tint = KeybookColors.TextFaint,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { showGenerator = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = "生成密码",
                                    tint = KeybookColors.Mint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
                if (editor.password.isNotBlank()) {
                    val score = PasswordGenerator.strength(editor.password)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(4) { index ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index < score) when (score) {
                                            1 -> KeybookColors.Danger
                                            2 -> KeybookColors.Gold
                                            else -> KeybookColors.Mint
                                        } else KeybookColors.OutlineSoft
                                    ),
                            )
                            if (index < 3) Spacer(Modifier.width(5.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            PasswordGenerator.strengthLabel(score),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                            color = KeybookColors.TextFaint,
                        )
                    }
                }
            }
        }

        if (editor.type == ItemType.TOTP) {
            item(key = "totp") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeybookTextField(
                        value = editor.totpSecret,
                        onValueChange = { input ->
                            val parsed = Totp.parseOtpAuth(input)
                            if (parsed != null) {
                                editor.totpSecret = parsed.config.secret
                                editor.title = if (editor.title.isBlank()) parsed.title else editor.title
                                editor.username = if (editor.username.isBlank()) parsed.username else editor.username
                                editor.totpAlgorithm = parsed.config.algorithm
                                editor.totpDigits = parsed.config.digits
                                editor.totpPeriod = parsed.config.period
                                importedOtp = true
                            } else {
                                editor.totpSecret = input
                                importedOtp = false
                            }
                            error = null
                        },
                        label = "TOTP 密钥",
                        placeholder = "Base32 密钥，或粘贴 otpauth:// 链接",
                        leadingIcon = Icons.Rounded.Timer,
                        visualTransformation = if (revealTotp) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = error?.contains("TOTP") == true,
                        supportingText = if (importedOtp) "已从 otpauth 链接自动填充" else "支持 2FA 迁移时提供的 Base32 密钥",
                        trailingIcon = {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { revealTotp = !revealTotp },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (revealTotp) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (revealTotp) "隐藏密钥" else "显示密钥",
                                    tint = KeybookColors.TextFaint,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )

                    if (totpPreview != null) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            borderColor = KeybookColors.Gold.copy(alpha = 0.18f),
                            colors = listOf(KeybookColors.Gold.copy(alpha = 0.06f), KeybookColors.Surface),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "实时预览",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = KeybookColors.Gold,
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        totpPreview.spacedCode,
                                        style = KeybookTypography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                        ),
                                        color = KeybookColors.Text,
                                    )
                                }
                                CountdownRing(
                                    totpPreview.progress,
                                    totpPreview.remainingSeconds,
                                    size = 46.dp,
                                    color = KeybookColors.Gold,
                                )
                            }
                        }
                    }

                    OptionChips(
                        label = "算法",
                        options = listOf("SHA1", "SHA256", "SHA512"),
                        selected = editor.totpAlgorithm,
                        onSelect = { editor.totpAlgorithm = it },
                    )
                    OptionChips(
                        label = "位数",
                        options = listOf("6", "8"),
                        selected = editor.totpDigits.toString(),
                        onSelect = { editor.totpDigits = it.toInt() },
                    )
                    OptionChips(
                        label = "刷新周期",
                        options = listOf("30", "60"),
                        selected = editor.totpPeriod.toString(),
                        onSelect = { editor.totpPeriod = it.toInt() },
                        suffix = " 秒",
                    )
                }
            }
        }

        if (editor.type == ItemType.PASSKEY) {
            item(key = "passkey") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeybookTextField(
                        value = editor.rpId,
                        onValueChange = {
                            editor.rpId = it
                            error = null
                        },
                        label = "依赖方 / RP ID",
                        placeholder = "example.com",
                        leadingIcon = Icons.Rounded.Fingerprint,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        isError = error?.contains("依赖方") == true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KeybookColors.Passkey.copy(alpha = 0.05f))
                            .border(1.dp, KeybookColors.Passkey.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(KeybookColors.Passkey.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = KeybookColors.Passkey,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "生成新的 ES256 密钥对",
                                style = MaterialTheme.typography.titleSmall,
                                color = KeybookColors.Text,
                            )
                            Text(
                                "用于新建通行密钥；已有凭证请直接粘贴字段",
                                style = MaterialTheme.typography.bodySmall,
                                color = KeybookColors.TextMuted,
                            )
                        }
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(KeybookColors.Passkey)
                                .clickable {
                                    val rp = editor.rpId.trim().ifBlank { editor.title.trim() }
                                    if (rp.isBlank()) {
                                        error = "请先填写标题或依赖方（RP ID）"
                                    } else {
                                        val generated = PasskeyFactory.generate(rp, editor.username)
                                        editor.rpId = rp
                                        editor.credentialId = generated.credentialId
                                        editor.privateKey = generated.privateKey
                                        editor.publicKey = generated.publicKey
                                        editor.userHandle = generated.userHandle
                                        revealPrivateKey = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "生成",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = KeybookColors.Ink,
                            )
                        }
                    }
                    KeybookTextField(
                        value = editor.credentialId,
                        onValueChange = { editor.credentialId = it },
                        label = "凭证 ID",
                        placeholder = "Base64URL 编码的 credential ID",
                        singleLine = false,
                        maxLines = 3,
                    )
                    KeybookTextField(
                        value = editor.userHandle,
                        onValueChange = { editor.userHandle = it },
                        label = "用户句柄（可选）",
                        placeholder = "Base64URL 编码的 user handle",
                        singleLine = false,
                        maxLines = 2,
                    )
                    KeybookTextField(
                        value = editor.publicKey,
                        onValueChange = { editor.publicKey = it },
                        label = "公钥 · SPKI",
                        placeholder = "Base64URL 编码的 DER 公钥",
                        singleLine = false,
                        maxLines = 4,
                    )
                    KeybookTextField(
                        value = editor.privateKey,
                        onValueChange = { editor.privateKey = it },
                        label = "私钥 · PKCS#8",
                        placeholder = "Base64 编码的私钥，将加密保存",
                        visualTransformation = if (revealPrivateKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = false,
                        maxLines = 5,
                        trailingIcon = {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { revealPrivateKey = !revealPrivateKey },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (revealPrivateKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (revealPrivateKey) "隐藏私钥" else "显示私钥",
                                    tint = KeybookColors.TextFaint,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        item(key = "notes") {
            KeybookTextField(
                value = editor.notes,
                onValueChange = { editor.notes = it },
                label = "备注（可选）",
                placeholder = "安全问题、恢复代码或其他说明",
                singleLine = false,
                minLines = 3,
                maxLines = 7,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
        }

        item(key = "favorite") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KeybookColors.Surface.copy(alpha = 0.55f))
                    .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(16.dp))
                    .clickable { editor.favorite = !editor.favorite }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (editor.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (editor.favorite) KeybookColors.Gold else KeybookColors.TextFaint,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("收藏此条目", style = MaterialTheme.typography.titleSmall, color = KeybookColors.Text)
                    Text(
                        "固定显示在列表顶部",
                        style = MaterialTheme.typography.bodySmall,
                        color = KeybookColors.TextMuted,
                    )
                }
                Switch(
                    checked = editor.favorite,
                    onCheckedChange = { editor.favorite = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = KeybookColors.Ink,
                        checkedTrackColor = KeybookColors.Gold,
                        uncheckedThumbColor = KeybookColors.TextFaint,
                        uncheckedTrackColor = KeybookColors.SurfaceSoft,
                        uncheckedBorderColor = KeybookColors.Outline,
                    ),
                )
            }
        }

        item(key = "error") {
            AnimatedError(error)
        }

        item(key = "save") {
            PrimaryButton(
                text = if (editor.editing) "保存修改" else "保存到保险箱",
                icon = Icons.Rounded.Shield,
                loading = busy,
                onClick = { save() },
            )
        }
    }

    if (showGenerator) {
        ModalBottomSheet(
            onDismissRequest = { showGenerator = false },
            sheetState = generatorSheet,
            containerColor = KeybookColors.Surface,
            scrimColor = KeybookColors.InkDeep.copy(alpha = 0.72f),
            dragHandle = {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    SheetHandle()
                }
            },
        ) {
            PasswordGeneratorSheet(
                onUse = { generated ->
                    editor.password = generated
                    revealPassword = true
                    showGenerator = false
                },
            )
        }
    }
}

@Composable
private fun OptionChips(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    suffix: String = "",
) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KeybookColors.TextFaint,
        )
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) KeybookColors.Mint else KeybookColors.Surface.copy(alpha = 0.7f))
                        .border(
                            1.dp,
                            if (isSelected) KeybookColors.Mint else KeybookColors.OutlineSoft,
                            CircleShape,
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        option + suffix,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (isSelected) KeybookColors.Ink else KeybookColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedError(error: String?) {
    androidx.compose.animation.AnimatedVisibility(
        visible = error != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
    ) {
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
                error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = KeybookColors.Danger,
            )
        }
    }
}

@Composable
private fun PasswordGeneratorSheet(onUse: (String) -> Unit) {
    var length by remember { mutableStateOf(20f) }
    var lowercase by remember { mutableStateOf(true) }
    var uppercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(false) }
    var generated by remember { mutableStateOf("") }

    fun regenerate() {
        generated = PasswordGenerator.generate(
            PasswordOptions(
                length = length.toInt(),
                lowercase = lowercase,
                uppercase = uppercase,
                digits = digits,
                symbols = symbols,
                excludeAmbiguous = excludeAmbiguous,
            )
        )
    }
    LaunchedEffect(length, lowercase, uppercase, digits, symbols, excludeAmbiguous) {
        regenerate()
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("生成强密码", style = MaterialTheme.typography.headlineMedium, color = KeybookColors.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用安全随机数在本机生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = KeybookColors.TextMuted,
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(KeybookColors.SurfaceRaised)
                    .clickable { regenerate() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "重新生成",
                    tint = KeybookColors.Mint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            borderColor = KeybookColors.Mint.copy(alpha = 0.2f),
            colors = listOf(KeybookColors.MintContainer.copy(alpha = 0.4f), KeybookColors.Surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    generated,
                    style = KeybookTypography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        letterSpacing = 0.8.sp,
                    ),
                    color = KeybookColors.MintStrong,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction(
                        text = "复制",
                        icon = Icons.Rounded.ContentCopy,
                        modifier = Modifier.weight(1f),
                        onClick = { SensitiveClipboard.copy(context, generated, "生成的密码") },
                    )
                    SmallAction(
                        text = "使用此密码",
                        icon = Icons.Rounded.Check,
                        modifier = Modifier.weight(1.3f),
                        emphasized = true,
                        onClick = { onUse(generated) },
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("长度", style = MaterialTheme.typography.titleSmall, color = KeybookColors.Text)
            Spacer(Modifier.weight(1f))
            Text(
                "${length.toInt()} 位",
                style = MaterialTheme.typography.labelLarge,
                color = KeybookColors.Mint,
            )
        }
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 12f..48f,
            steps = 35,
            colors = SliderDefaults.colors(
                thumbColor = KeybookColors.Mint,
                activeTrackColor = KeybookColors.Mint,
                inactiveTrackColor = KeybookColors.Outline,
            ),
        )
        ToggleRow("小写字母", "a-z", lowercase) { lowercase = it }
        ToggleRow("大写字母", "A-Z", uppercase) { uppercase = it }
        ToggleRow("数字", "0-9", digits) { digits = it }
        ToggleRow("符号", "!@#\$…", symbols) { symbols = it }
        ToggleRow("排除易混淆字符", "0 O l I", excludeAmbiguous) { excludeAmbiguous = it }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = KeybookColors.Text)
        Spacer(Modifier.width(7.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = KeybookColors.TextFaint)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KeybookColors.Ink,
                checkedTrackColor = KeybookColors.Mint,
                uncheckedThumbColor = KeybookColors.TextFaint,
                uncheckedTrackColor = KeybookColors.SurfaceSoft,
                uncheckedBorderColor = KeybookColors.Outline,
            ),
        )
    }
}

@Composable
private fun SmallAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (emphasized) KeybookColors.Mint else KeybookColors.Surface.copy(alpha = 0.8f))
            .border(
                1.dp,
                if (emphasized) KeybookColors.Mint else KeybookColors.Outline,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (emphasized) KeybookColors.Ink else KeybookColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (emphasized) KeybookColors.Ink else KeybookColors.Text,
        )
    }
}

