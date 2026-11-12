package com.keybook.vault.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultItem
import com.keybook.vault.ui.VaultViewModel
import com.keybook.vault.ui.components.CountdownRing
import com.keybook.vault.ui.components.FieldDivider
import com.keybook.vault.ui.components.GlassCard
import com.keybook.vault.ui.components.PrimaryButton
import com.keybook.vault.ui.components.RoundIconButton
import com.keybook.vault.ui.components.ScreenTopBar
import com.keybook.vault.ui.components.TypeGlyph
import com.keybook.vault.ui.components.TypePill
import com.keybook.vault.ui.components.rememberTotpState
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.ui.theme.KeybookTypography
import com.keybook.vault.util.Formatters
import com.keybook.vault.util.SensitiveClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    item: VaultItem,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var revealedPassword by remember(item.id) { mutableStateOf(false) }
    var revealedPrivateKey by remember(item.id) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val totp = if (item.type == ItemType.TOTP) {
        rememberTotpState(item.totpSecret, item.totpAlgorithm, item.totpDigits, item.totpPeriod)
    } else {
        null
    }

    LaunchedEffect(revealedPassword) {
        if (revealedPassword) {
            delay(20_000)
            revealedPassword = false
        }
    }
    LaunchedEffect(revealedPrivateKey) {
        if (revealedPrivateKey) {
            delay(20_000)
            revealedPrivateKey = false
        }
    }

    fun copy(value: String, label: String) {
        if (value.isBlank()) return
        SensitiveClipboard.copy(context, value, label)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.message("已复制到剪贴板 · 60 秒后自动清除")
    }

    fun openUrl() {
        if (item.url.isBlank()) return
        val normalized = if (item.url.startsWith("http://") || item.url.startsWith("https://")) {
            item.url
        } else {
            "https://${item.url}"
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
        }.onFailure {
            viewModel.message("无法打开这个网址")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "top") {
            ScreenTopBar(
                title = "",
                onBack = onBack,
                actions = {
                    RoundIconButton(
                        icon = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (item.favorite) "取消收藏" else "收藏",
                        tint = if (item.favorite) KeybookColors.Gold else KeybookColors.TextMuted,
                        onClick = {
                            scope.launch {
                                viewModel.setFavorite(item.id, !item.favorite)
                            }
                        },
                    )
                    Spacer(Modifier.width(9.dp))
                    Box {
                        RoundIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "更多操作",
                            onClick = { menuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = KeybookColors.SurfaceRaised,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑条目", color = KeybookColors.Text) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = KeybookColors.TextMuted)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除条目", color = KeybookColors.Danger) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = KeybookColors.Danger,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
            )
        }

        item(key = "hero") {
            Column(Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeGlyph(item.type, size = 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        TypePill(item.type)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.title.ifBlank { "未命名" },
                            style = MaterialTheme.typography.displaySmall,
                            color = KeybookColors.Text,
                        )
                    }
                }
                if (item.url.isNotBlank()) {
                    Spacer(Modifier.height(15.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(KeybookColors.Surface.copy(alpha = 0.7f))
                            .border(1.dp, KeybookColors.OutlineSoft, CircleShape)
                            .clickable { openUrl() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.url,
                            style = MaterialTheme.typography.labelMedium,
                            color = KeybookColors.Mint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = "打开网站",
                            tint = KeybookColors.MintDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        when (item.type) {
            ItemType.PASSWORD -> {
                item(key = "password") {
                    PasswordPanel(
                        item = item,
                        revealed = revealedPassword,
                        onToggleReveal = { revealedPassword = !revealedPassword },
                        onCopy = { copy(item.password, "${item.title} 密码") },
                    )
                }
            }

            ItemType.TOTP -> {
                item(key = "totp") {
                    TotpPanel(
                        item = item,
                        totp = totp,
                        onCopy = { totp?.let { copy(it.code, "${item.title} 验证码") } },
                    )
                }
            }

            ItemType.PASSKEY -> {
                item(key = "passkey") {
                    PasskeyPanel(
                        item = item,
                        revealed = revealedPrivateKey,
                        onToggleReveal = { revealedPrivateKey = !revealedPrivateKey },
                        onCopy = { copy(it, "${item.title} 通行密钥") },
                    )
                }
            }
        }

        if (item.username.isNotBlank() && item.type != ItemType.PASSKEY) {
            item(key = "username") {
                ValueRow(
                    label = "账户 / 用户名",
                    value = item.username,
                    onCopy = { copy(item.username, "${item.title} 账户") },
                )
            }
        }

        if (item.type == ItemType.TOTP && item.totpSecret.isNotBlank()) {
            item(key = "secret") {
                ValueRow(
                    label = "TOTP 密钥",
                    value = "•••••••• •••• ••••",
                    onCopy = { copy(item.totpSecret, "${item.title} TOTP 密钥") },
                )
            }
        }

        if (item.notes.isNotBlank()) {
            item(key = "notes") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Notes,
                                contentDescription = null,
                                tint = KeybookColors.TextFaint,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "备注",
                                style = MaterialTheme.typography.labelSmall,
                                color = KeybookColors.TextFaint,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            item.notes,
                            style = MaterialTheme.typography.bodyLarge,
                            color = KeybookColors.TextMuted,
                            lineHeight = 23.sp,
                        )
                    }
                }
            }
        }

        item(key = "edit") {
            PrimaryButton(
                text = "编辑条目",
                icon = Icons.Rounded.Edit,
                onClick = onEdit,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        item(key = "meta") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "创建于 ${Formatters.dateTime(item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
                    color = KeybookColors.TextFaint,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "更新于 ${Formatters.dateTime(item.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
                    color = KeybookColors.TextFaint,
                )
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDelete = false },
            containerColor = KeybookColors.SurfaceRaised,
            titleContentColor = KeybookColors.Text,
            textContentColor = KeybookColors.TextMuted,
            title = { Text("删除这个条目？", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "“${item.title}”将从加密保险箱中永久移除，此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            val result = viewModel.delete(item.id)
                            if (result.isFailure) {
                                viewModel.message(viewModel.messageFor(result.exceptionOrNull()!!))
                                deleting = false
                                showDelete = false
                            } else {
                                onBack()
                            }
                        }
                    },
                ) {
                    Text("永久删除", color = KeybookColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }, enabled = !deleting) {
                    Text("取消", color = KeybookColors.TextMuted)
                }
            },
        )
    }
}

@Composable
private fun PasswordPanel(
    item: VaultItem,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        borderColor = KeybookColors.Mint.copy(alpha = 0.16f),
        colors = listOf(KeybookColors.MintContainer.copy(alpha = 0.38f), KeybookColors.Surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = KeybookColors.Mint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "登录密码",
                    style = MaterialTheme.typography.labelSmall,
                    color = KeybookColors.MintDim,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (revealed) "显示中" else "已隐藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (revealed) KeybookColors.Gold else KeybookColors.TextFaint,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (item.password.isBlank()) "未设置密码"
                else if (revealed) item.password else "••••••••••••••••",
                style = if (revealed) KeybookTypography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    letterSpacing = 0.6.sp,
                ) else MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
                color = if (revealed) KeybookColors.Text else KeybookColors.TextMuted,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActionPill(
                    text = if (revealed) "隐藏" else "显示",
                    icon = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleReveal,
                )
                ActionPill(
                    text = "复制",
                    icon = Icons.Rounded.ContentCopy,
                    modifier = Modifier.weight(1f),
                    enabled = item.password.isNotBlank(),
                    onClick = onCopy,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "密码默认隐藏 · 显示 20 秒后自动收起",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
                color = KeybookColors.TextFaint,
            )
        }
    }
}

@Composable
private fun TotpPanel(item: VaultItem, totp: com.keybook.vault.totp.TotpState?, onCopy: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        borderColor = KeybookColors.Gold.copy(alpha = 0.18f),
        colors = listOf(KeybookColors.Gold.copy(alpha = 0.08f), KeybookColors.Surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "动态验证码",
                    style = MaterialTheme.typography.labelSmall,
                    color = KeybookColors.Gold.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "每 ${item.totpPeriod} 秒更新",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
                    color = KeybookColors.TextFaint,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (totp == null) {
                Text(
                    "无法解析这个 TOTP 密钥，请检查密钥与算法设置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KeybookColors.Danger,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        totp.spacedCode,
                        modifier = Modifier.weight(1f),
                        style = KeybookTypography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 3.sp,
                            fontSize = 34.sp,
                        ),
                        color = KeybookColors.Text,
                    )
                    CountdownRing(
                        progress = totp.progress,
                        seconds = totp.remainingSeconds,
                        size = 52.dp,
                        color = KeybookColors.Gold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(KeybookColors.OutlineSoft),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(totp.progress)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (totp.progress < 0.2f) KeybookColors.Danger else KeybookColors.Gold
                            ),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ActionPill(
                    text = "复制验证码",
                    icon = Icons.Rounded.ContentCopy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopy,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "验证码基于时间生成，请勿通过短信或聊天发送。",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
                color = KeybookColors.TextFaint,
            )
        }
    }
}

@Composable
private fun PasskeyPanel(
    item: VaultItem,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            borderColor = KeybookColors.Passkey.copy(alpha = 0.18f),
            colors = listOf(KeybookColors.Passkey.copy(alpha = 0.07f), KeybookColors.Surface),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "通行密钥",
                        style = MaterialTheme.typography.labelSmall,
                        color = KeybookColors.Passkey,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        item.passkeyAlgorithm.ifBlank { "ES256" },
                        style = MaterialTheme.typography.labelSmall,
                        color = KeybookColors.TextFaint,
                    )
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    item.rpId.ifBlank { "未填写依赖方" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = KeybookColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.username.isNotBlank() && item.type != ItemType.PASSKEY) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = KeybookColors.TextMuted,
                    )
                }
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column {
                ValueRowContent(
                    label = "凭证 ID",
                    value = item.credentialId.ifBlank { "未填写" },
                    mono = true,
                    modifier = Modifier.padding(16.dp),
                    onCopy = if (item.credentialId.isBlank()) null else {
                        { onCopy(item.credentialId) }
                    },
                )
                FieldDivider(Modifier.padding(horizontal = 16.dp))
                ValueRowContent(
                    label = "用户句柄",
                    value = item.userHandle.ifBlank { "未填写" },
                    mono = true,
                    modifier = Modifier.padding(16.dp),
                    onCopy = if (item.userHandle.isBlank()) null else {
                        { onCopy(item.userHandle) }
                    },
                )
                FieldDivider(Modifier.padding(horizontal = 16.dp))
                ValueRowContent(
                    label = "公钥 · SPKI",
                    value = item.publicKey.ifBlank { "未填写" },
                    mono = true,
                    modifier = Modifier.padding(16.dp),
                    onCopy = if (item.publicKey.isBlank()) null else {
                        { onCopy(item.publicKey) }
                    },
                )
                FieldDivider(Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "私钥 · PKCS#8",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeybookColors.TextFaint,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onToggleReveal() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (revealed) "隐藏私钥" else "显示私钥",
                                tint = if (revealed) KeybookColors.Passkey else KeybookColors.TextFaint,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(enabled = item.privateKey.isNotBlank() && revealed) {
                                    onCopy(item.privateKey)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "复制私钥",
                                tint = if (revealed) KeybookColors.TextFaint else KeybookColors.Outline,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (item.privateKey.isBlank()) "未填写"
                        else if (revealed) item.privateKey
                        else "••••••••••••••••••••••••••••••••",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (revealed) FontFamily.Monospace else FontFamily.SansSerif,
                            letterSpacing = if (revealed) 0.1.sp else 1.6.sp,
                        ),
                        color = KeybookColors.TextMuted,
                        maxLines = if (revealed) 8 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (revealed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请谨慎复制私钥。任何获得私钥的人都可以冒充这个通行密钥。",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
                            color = KeybookColors.Danger.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
    trailingLabel: String? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        ValueRowContent(
            label = label,
            value = value,
            onCopy = onCopy,
            trailingLabel = trailingLabel,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ValueRowContent(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
    trailingLabel: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = KeybookColors.TextFaint,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
                    fontSize = if (mono) 12.sp else 15.sp,
                ),
                color = KeybookColors.Text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingLabel != null) {
            Text(
                trailingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = KeybookColors.MintDim,
            )
        }
        if (onCopy != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(KeybookColors.Surface.copy(alpha = 0.7f))
                    .clickable(onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "复制$label",
                    tint = KeybookColors.TextFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KeybookColors.Surface.copy(alpha = 0.8f))
            .border(1.dp, KeybookColors.Outline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) KeybookColors.Mint else KeybookColors.TextFaint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) KeybookColors.Text else KeybookColors.TextFaint,
        )
    }
}

