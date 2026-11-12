package com.keybook.vault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultData
import com.keybook.vault.data.VaultItem
import com.keybook.vault.data.label
import com.keybook.vault.totp.TotpState
import com.keybook.vault.ui.VaultViewModel
import com.keybook.vault.ui.components.CountdownRing
import com.keybook.vault.ui.components.DashedHalo
import com.keybook.vault.ui.components.GlassCard
import com.keybook.vault.ui.components.PrimaryButton
import com.keybook.vault.ui.components.RoundIconButton
import com.keybook.vault.ui.components.SheetHandle
import com.keybook.vault.ui.components.TypeGlyph
import com.keybook.vault.ui.components.VaultMark
import com.keybook.vault.ui.components.rememberTotpState
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.ui.theme.KeybookTypography
import com.keybook.vault.util.SensitiveClipboard
import kotlinx.coroutines.delay

private enum class VaultFilter(val label: String) {
    ALL("全部"),
    PASSWORD("密码"),
    TOTP("验证码"),
    PASSKEY("通行密钥"),
    FAVORITES("收藏"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vault: VaultData,
    viewModel: VaultViewModel,
    onOpenItem: (String) -> Unit,
    onAddItem: (ItemType) -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(VaultFilter.ALL) }
    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val total = vault.items.size
    val counts = remember(vault.items) {
        mapOf(
            VaultFilter.ALL to vault.items.size,
            VaultFilter.PASSWORD to vault.items.count { it.type == ItemType.PASSWORD },
            VaultFilter.TOTP to vault.items.count { it.type == ItemType.TOTP },
            VaultFilter.PASSKEY to vault.items.count { it.type == ItemType.PASSKEY },
            VaultFilter.FAVORITES to vault.items.count { it.favorite },
        )
    }
    val filtered = remember(vault.items, query, filter) {
        vault.items.asSequence()
            .filter { item ->
                when (filter) {
                    VaultFilter.ALL -> true
                    VaultFilter.PASSWORD -> item.type == ItemType.PASSWORD
                    VaultFilter.TOTP -> item.type == ItemType.TOTP
                    VaultFilter.PASSKEY -> item.type == ItemType.PASSKEY
                    VaultFilter.FAVORITES -> item.favorite
                }
            }
            .filter { it.matches(query) }
            .sortedWith(compareByDescending<VaultItem> { it.favorite }.thenByDescending { it.updatedAt })
            .toList()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VaultMark(size = 38.dp, glow = false)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            "KEYBOOK",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.6.sp),
                            color = KeybookColors.Mint,
                        )
                        Text(
                            "密码本",
                            style = MaterialTheme.typography.titleMedium,
                            color = KeybookColors.Text,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(
                        icon = Icons.Rounded.Lock,
                        contentDescription = "立即锁定",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLock()
                        },
                    )
                    Spacer(Modifier.width(9.dp))
                    RoundIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "设置",
                        onClick = onSettings,
                    )
                }
            }

            item(key = "search") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                )
            }

            item(key = "filters") {
                if (total > 0) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        VaultFilter.entries.forEach { option ->
                            item(key = option.name) {
                                FilterPill(
                                    label = option.label,
                                    count = counts[option] ?: 0,
                                    selected = filter == option,
                                    onClick = { filter = option },
                                )
                            }
                        }
                    }
                }
            }

            if (total > 0) {
                item(key = "section") {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when {
                                query.isNotBlank() -> "搜索结果"
                                filter == VaultFilter.ALL -> "所有条目"
                                filter == VaultFilter.FAVORITES -> "收藏条目"
                                else -> filter.label
                            }.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = KeybookColors.TextFaint,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${filtered.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeybookColors.TextFaint,
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(key = "empty") {
                    EmptyVault(
                        hasAnyItem = total > 0,
                        query = query,
                        onAdd = { showAddSheet = true },
                    )
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    EntryCard(
                        item = item,
                        onClick = { onOpenItem(item.id) },
                        onCopied = viewModel::message,
                    )
                }
            }
        }

        if (total > 0) {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = KeybookColors.Ink)
                },
                text = {
                    Text(
                        "新建",
                        color = KeybookColors.Ink,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 20.dp),
                containerColor = KeybookColors.Mint,
                contentColor = KeybookColors.Ink,
                shape = RoundedCornerShape(19.dp),
            )
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = KeybookColors.Surface,
            scrimColor = KeybookColors.InkDeep.copy(alpha = 0.7f),
            dragHandle = {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    SheetHandle()
                }
            },
        ) {
            AddItemSheet(
                onSelect = { type ->
                    showAddSheet = false
                    onAddItem(type)
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(KeybookColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = KeybookColors.TextFaint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = KeybookColors.Text),
            cursorBrush = SolidColor(KeybookColors.Mint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "搜索标题、账户或网站",
                            style = MaterialTheme.typography.bodyLarge,
                            color = KeybookColors.TextFaint,
                        )
                    }
                    inner()
                }
            },
        )
        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Clear,
                    contentDescription = "清空搜索",
                    tint = KeybookColors.TextFaint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) KeybookColors.Mint else KeybookColors.Surface.copy(alpha = 0.68f),
        label = "filter-bg",
    )
    val content by animateColorAsState(
        targetValue = if (selected) KeybookColors.Ink else KeybookColors.TextMuted,
        label = "filter-fg",
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .border(
                1.dp,
                if (selected) KeybookColors.Mint else KeybookColors.OutlineSoft,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = content,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            color = if (selected) KeybookColors.Ink.copy(alpha = 0.6f) else KeybookColors.TextFaint,
        )
    }
}

@Composable
private fun EntryCard(
    item: VaultItem,
    onClick: () -> Unit,
    onCopied: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var revealed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(20_000)
            revealed = false
        }
    }
    val totp = if (item.type == ItemType.TOTP) {
        rememberTotpState(item.totpSecret, item.totpAlgorithm, item.totpDigits, item.totpPeriod)
    } else {
        null
    }
    fun copy(value: String, label: String) {
        if (value.isBlank()) return
        SensitiveClipboard.copy(context, value, label)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onCopied("已复制到剪贴板 · 60 秒后自动清除")
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        borderColor = if (item.favorite) KeybookColors.Gold.copy(alpha = 0.16f) else KeybookColors.OutlineSoft,
        colors = if (item.favorite) {
            listOf(KeybookColors.SurfaceSoft.copy(alpha = 0.92f), KeybookColors.Surface)
        } else {
            listOf(KeybookColors.SurfaceRaised.copy(alpha = 0.94f), KeybookColors.Surface)
        },
        onClick = onClick,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeGlyph(item.type, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.favorite) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = "收藏",
                                tint = KeybookColors.Gold,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            item.title.ifBlank { "未命名" },
                            style = MaterialTheme.typography.titleMedium,
                            color = KeybookColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = KeybookColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = KeybookColors.TextFaint,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            when (item.type) {
                ItemType.PASSWORD -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KeybookColors.Ink.copy(alpha = 0.55f))
                        .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (item.password.isBlank()) "未设置密码"
                        else if (revealed) item.password else "••••••••••••",
                        modifier = Modifier.weight(1f),
                        style = KeybookTypography.bodyMedium.copy(
                            fontFamily = if (revealed) FontFamily.Monospace else FontFamily.SansSerif,
                            letterSpacing = if (revealed) 0.4.sp else 2.sp,
                            color = if (revealed) KeybookColors.Text else KeybookColors.TextMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.password.isNotBlank()) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    revealed = !revealed
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (revealed) "隐藏密码" else "显示密码",
                                tint = if (revealed) KeybookColors.Mint else KeybookColors.TextFaint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { copy(item.password, "${item.title} 密码") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "复制密码",
                                tint = KeybookColors.TextFaint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                ItemType.TOTP -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KeybookColors.Ink.copy(alpha = 0.55f))
                        .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (totp != null) "*** ***" else "无效的验证码密钥",
                        modifier = Modifier.weight(1f),
                        style = if (totp != null) KeybookTypography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Medium,
                        ) else MaterialTheme.typography.bodySmall,
                        color = if (totp != null) KeybookColors.Gold else KeybookColors.Danger,
                    )
                    if (totp != null) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { copy(totp.code, "${item.title} 验证码") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "复制验证码",
                                tint = KeybookColors.TextFaint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        CountdownRing(
                            progress = totp.progress,
                            seconds = totp.remainingSeconds,
                            size = 38.dp,
                        )
                    }
                }

                ItemType.PASSKEY -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KeybookColors.Ink.copy(alpha = 0.45f))
                        .border(1.dp, KeybookColors.OutlineSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = KeybookColors.Passkey,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (item.credentialId.isBlank()) "通行密钥材料未填写完整"
                        else "凭证 ${item.credentialId.take(12)}…",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.2.sp,
                        ),
                        color = KeybookColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        item.passkeyAlgorithm.ifBlank { "ES256" },
                        style = MaterialTheme.typography.labelSmall,
                        color = KeybookColors.Passkey,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVault(hasAnyItem: Boolean, query: String, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            DashedHalo(Modifier.size(78.dp)) {
                VaultMark(size = 44.dp, glow = false)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (hasAnyItem) "没有找到匹配项" else "保险箱还是空的",
            style = MaterialTheme.typography.headlineSmall,
            color = KeybookColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasAnyItem) "换个关键词，或切换到其他分类试试。"
            else "所有条目只在本机以密文保存。\n先放一个密码、验证码或通行密钥进来吧。",
            style = MaterialTheme.typography.bodySmall,
            color = KeybookColors.TextMuted,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!hasAnyItem) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "添加第一个条目",
                modifier = Modifier.width(226.dp),
                icon = Icons.Rounded.Add,
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun AddItemSheet(onSelect: (ItemType) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 22.dp),
    ) {
        Text("新建条目", style = MaterialTheme.typography.headlineMedium, color = KeybookColors.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            "选择要保存到加密保险箱的内容",
            style = MaterialTheme.typography.bodyMedium,
            color = KeybookColors.TextMuted,
        )
        Spacer(Modifier.height(18.dp))
        AddOption(
            type = ItemType.PASSWORD,
            title = "密码",
            description = "网站、应用账号与登录密码",
            onClick = { onSelect(ItemType.PASSWORD) },
        )
        Spacer(Modifier.height(10.dp))
        AddOption(
            type = ItemType.TOTP,
            title = "动态验证码",
            description = "保存 TOTP 密钥，持续生成 6 位验证码",
            onClick = { onSelect(ItemType.TOTP) },
        )
        Spacer(Modifier.height(10.dp))
        AddOption(
            type = ItemType.PASSKEY,
            title = "通行密钥",
            description = "保存 WebAuthn 通行密钥与 ES256 密钥对",
            onClick = { onSelect(ItemType.PASSKEY) },
        )
    }
}

@Composable
private fun AddOption(type: ItemType, title: String, description: String, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeGlyph(type, size = 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = KeybookColors.Text)
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KeybookColors.TextMuted,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = KeybookColors.TextFaint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}



