package com.keybook.vault.passkeys

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.keybook.vault.BuildConfig
import com.keybook.vault.data.ItemType
import com.keybook.vault.data.VaultItem
import com.keybook.vault.data.VaultRepository
import com.keybook.vault.security.PasskeyFactory
import com.keybook.vault.ui.BiometricAuthenticator
import com.keybook.vault.ui.components.AuroraBackground
import com.keybook.vault.ui.components.GlassCard
import com.keybook.vault.ui.components.KeybookTextField
import com.keybook.vault.ui.components.PrimaryButton
import com.keybook.vault.ui.components.SecondaryButton
import com.keybook.vault.ui.components.TypeGlyph
import com.keybook.vault.ui.components.VaultMark
import com.keybook.vault.ui.screens.InlineError
import com.keybook.vault.ui.theme.KeybookColors
import com.keybook.vault.ui.theme.KeybookTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PasskeyRequestActivity : FragmentActivity() {
    private enum class Mode { CREATE, GET, UNLOCK, INVALID }

    private lateinit var repository: VaultRepository
    private var mode = Mode.INVALID
    private var wasLocked = false
    private var initError: String? = null

    private var createRequest: ProviderCreateCredentialRequest? = null
    private var getRequest: ProviderGetCredentialRequest? = null
    private var beginGetRequest: BeginGetCredentialRequest? = null
    private var creationOptions: CreationOptions? = null
    private var assertionOptions: AssertionOptions? = null
    private var clientDataHash: ByteArray? = null
    private var credentialIdExtra = ""
    private var callingPackage = ""
    private var callingLabel = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        repository = VaultRepository.get(this)
        repository.refreshSecurity()
        wasLocked = !repository.isUnlocked()

        try {
            when (intent?.action) {
                PasskeyProviderEngine.ACTION_CREATE -> prepareCreate()
                PasskeyProviderEngine.ACTION_GET -> prepareGet()
                PasskeyProviderEngine.ACTION_UNLOCK -> prepareUnlock()
                else -> initError = "无法识别的通行密钥请求"
            }
        } catch (e: Exception) {
            initError = e.message ?: "无法读取通行密钥请求"
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelFlow()
            }
        })

        setContent {
            KeybookTheme {
                PasskeyRequestScreen()
            }
        }
    }

    private fun prepareCreate() {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
            ?: throw PasskeyProtocolException("缺少创建通行密钥请求")
        createRequest = request
        val calling = request.callingAppInfo
        callingPackage = calling.packageName
        callingLabel = appLabel(calling.packageName)
        val callingRequest = request.callingRequest as? androidx.credentials.CreatePublicKeyCredentialRequest
            ?: throw PasskeyProtocolException("不支持的凭据类型")
        clientDataHash = callingRequest.clientDataHash
        creationOptions = WebAuthn.parseCreationOptions(callingRequest.requestJson)
        mode = Mode.CREATE
    }

    private fun prepareGet() {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
            ?: throw PasskeyProtocolException("缺少读取通行密钥请求")
        getRequest = request
        callingPackage = request.callingAppInfo.packageName
        callingLabel = appLabel(request.callingAppInfo.packageName)
        val option = request.credentialOptions.filterIsInstance<GetPublicKeyCredentialOption>().firstOrNull()
            ?: throw PasskeyProtocolException("不支持的凭据类型")
        clientDataHash = option.clientDataHash
        assertionOptions = WebAuthn.parseAssertionOptions(option.requestJson)
        credentialIdExtra = intent?.getStringExtra(PasskeyProviderEngine.EXTRA_CREDENTIAL_ID).orEmpty()
        mode = Mode.GET
    }

    private fun prepareUnlock() {
        beginGetRequest = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
            ?: throw PasskeyProtocolException("缺少解锁请求")
        callingPackage = beginGetRequest?.callingAppInfo?.packageName.orEmpty()
        callingLabel = callingPackage.takeIf { it.isNotBlank() }?.let(::appLabel).orEmpty()
        mode = Mode.UNLOCK
    }

    private fun appLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun cancelFlow() {
        if (wasLocked && repository.isUnlocked()) {
            repository.lock()
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishProviderResponse(result: Intent, lockAfter: Boolean) {
        if (lockAfter) repository.lock()
        setResult(RESULT_OK, result)
        finish()
    }

    private suspend fun completeCreate(): String? {
        val options = creationOptions ?: return "创建请求已损坏"
        val providerRequest = createRequest ?: return "创建请求已损坏"
        val origin = PasskeyProviderEngine.appOrigin(providerRequest.callingAppInfo)
        if (!PasskeyProviderEngine.isRpIdAllowed(options.rpId, origin)) {
            return "应用身份与依赖方不匹配"
        }
        val generated = PasskeyFactory.generate(
            rpId = options.rpId,
            username = options.userName.ifBlank { options.userDisplayName },
            userHandleOverride = options.userId.takeIf { it.isNotBlank() },
        )
        val item = VaultItem(
            type = ItemType.PASSKEY,
            title = options.rpName.ifBlank { options.rpId },
            username = options.userName.ifBlank { options.userDisplayName },
            rpId = options.rpId,
            credentialId = generated.credentialId,
            privateKey = generated.privateKey,
            publicKey = generated.publicKey,
            userHandle = generated.userHandle,
            notes = "由系统通行密钥请求创建",
            passkeySignCount = 0,
        )
        val responseJson = try {
            WebAuthn.buildAttestationResponse(
                options = options,
                credentialId = generated.credentialId,
                publicKeySpki = generated.publicKey,
                clientDataHash = clientDataHash,
                origin = origin,
                packageName = providerRequest.callingAppInfo.packageName,
            )
        } catch (e: Exception) {
            return e.message ?: "无法生成通行密钥"
        }
        val saved = repository.upsert(item)
        if (saved.isFailure) return saved.exceptionOrNull()?.message ?: "无法保存通行密钥"
        val result = Intent()
        PendingIntentHandler.setCreateCredentialResponse(
            result,
            CreatePublicKeyCredentialResponse(responseJson),
        )
        finishProviderResponse(result, lockAfter = true)
        return null
    }

    private suspend fun completeGet(): String? {
        val options = assertionOptions ?: return "请求已损坏"
        val providerRequest = getRequest ?: return "请求已损坏"
        val credentialId = credentialIdExtra.ifBlank {
            options.allowCredentialIds.firstOrNull().orEmpty()
        }
        if (credentialId.isBlank()) return "缺少凭证 ID"
        val item = repository.snapshot()?.items?.firstOrNull {
            it.type == ItemType.PASSKEY && it.credentialId == credentialId
        } ?: return "密码本中没有这个通行密钥"
        if (item.rpId != options.rpId) return "通行密钥与依赖方不匹配"
        val origin = PasskeyProviderEngine.appOrigin(providerRequest.callingAppInfo)
        if (!PasskeyProviderEngine.isRpIdAllowed(options.rpId, origin)) {
            return "应用身份与依赖方不匹配"
        }
        val nextCount = item.passkeySignCount + 1
        val responseJson = try {
            WebAuthn.buildAssertionResponse(
                options = options,
                item = item,
                signCount = nextCount,
                origin = origin,
                packageName = providerRequest.callingAppInfo.packageName,
                clientDataHash = clientDataHash,
            )
        } catch (e: Exception) {
            return e.message ?: "无法生成通行密钥签名"
        }
        val updated = repository.updatePasskeySignCount(item.id, nextCount)
        if (updated.isFailure) return updated.exceptionOrNull()?.message ?: "无法更新通行密钥"
        val result = Intent()
        PendingIntentHandler.setGetCredentialResponse(
            result,
            GetCredentialResponse(PublicKeyCredential(responseJson)),
        )
        finishProviderResponse(result, lockAfter = true)
        return null
    }

    private fun completeUnlock(): String? {
        val request = beginGetRequest ?: return "解锁请求已损坏"
        val response = try {
            PasskeyProviderEngine.buildGetResponse(this, request)
        } catch (e: Exception) {
            return e.message ?: "没有可用的通行密钥"
        }
        val result = Intent()
        PendingIntentHandler.setBeginGetCredentialResponse(result, response)
        finishProviderResponse(result, lockAfter = false)
        return null
    }

    @Composable
    private fun PasskeyRequestScreen() {
        val security by repository.security.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val activity = this
        val scope = rememberCoroutineScope()
        var unlocked by remember { mutableStateOf(repository.isUnlocked()) }
        var password by remember { mutableStateOf("") }
        var visible by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(initError) }
        var autoPrompted by remember { mutableStateOf(false) }
        var promptVisible by remember { mutableStateOf(false) }

        fun unlockWithPassword() {
            if (busy || password.isBlank()) return
            busy = true
            error = null
            scope.launch {
                val chars = password.toCharArray()
                val result = try {
                    repository.unlock(chars)
                } finally {
                    chars.fill('\u0000')
                }
                if (result.isSuccess) {
                    unlocked = true
                    password = ""
                } else {
                    error = result.exceptionOrNull()?.message ?: "主密码不正确"
                }
                busy = false
            }
        }

        fun promptBiometric() {
            if (promptVisible || busy) return
            val cipher = repository.prepareBiometricUnlock().getOrElse {
                error = it.message ?: "指纹解锁不可用"
                return
            }
            promptVisible = true
            BiometricAuthenticator(activity).authenticate(
                title = "解锁密码本",
                subtitle = "验证指纹以使用通行密钥",
                negativeText = "使用主密码",
                cipher = cipher,
                onSuccess = { authenticated ->
                    scope.launch {
                        busy = true
                        val result = repository.unlockWithBiometric(authenticated)
                        if (result.isSuccess) {
                            unlocked = true
                        } else {
                            error = result.exceptionOrNull()?.message ?: "指纹解锁失败"
                        }
                        promptVisible = false
                        busy = false
                    }
                },
                onError = {
                    promptVisible = false
                    error = it
                },
                onCancel = { promptVisible = false },
            )
        }

        LaunchedEffect(Unit) {
            if (mode != Mode.INVALID && repository.isInitialized() && !unlocked &&
                security.biometricEnabled && security.biometricAvailable && !autoPrompted
            ) {
                autoPrompted = true
                delay(320)
                promptBiometric()
            }
        }

        LaunchedEffect(unlocked) {
            if (unlocked && mode == Mode.UNLOCK && error == null) {
                busy = true
                val message = completeUnlock()
                if (message != null) {
                    error = message
                    busy = false
                }
            }
        }

        AuroraBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.size(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VaultMark(size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "KEYBOOK",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.6.sp),
                            color = KeybookColors.Mint,
                        )
                        Text(
                            "密码本通行密钥",
                            style = MaterialTheme.typography.titleMedium,
                            color = KeybookColors.Text,
                        )
                    }
                }
                Spacer(Modifier.size(30.dp))
                Text(
                    when (mode) {
                        Mode.CREATE -> "添加通行密钥"
                        Mode.GET -> "使用通行密钥"
                        Mode.UNLOCK -> "解锁通行密钥"
                        Mode.INVALID -> "无法处理请求"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = KeybookColors.Text,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    when (mode) {
                        Mode.CREATE -> "有一个应用希望将通行密钥保存到你的加密保险箱。"
                        Mode.GET -> "验证身份后，密码本将代表你完成通行密钥签名。"
                        Mode.UNLOCK -> "系统需要读取密码本中的通行密钥。"
                        Mode.INVALID -> initError ?: "通行密钥请求已失效。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = KeybookColors.TextMuted,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.size(20.dp))

                val rpId = creationOptions?.rpId ?: assertionOptions?.rpId
                if (rpId != null) {
                    val displayItem = repository.snapshot()?.items?.firstOrNull {
                        it.credentialId == credentialIdExtra
                    }
                    val displayTitle = when (mode) {
                        Mode.CREATE -> creationOptions?.rpName?.ifBlank { rpId } ?: rpId
                        Mode.GET -> displayItem?.title?.ifBlank { rpId } ?: rpId
                        else -> rpId
                    }
                    val displayAccount = creationOptions?.userName?.ifBlank {
                        creationOptions?.userDisplayName
                    } ?: displayItem?.username
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        borderColor = KeybookColors.Passkey.copy(alpha = 0.18f),
                        colors = listOf(KeybookColors.SurfaceRaised, KeybookColors.Surface),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeGlyph(ItemType.PASSKEY, size = 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KeybookColors.Text,
                                )
                                if (displayTitle != rpId) {
                                    Spacer(Modifier.size(3.dp))
                                    Text(
                                        rpId,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        color = KeybookColors.TextMuted,
                                    )
                                }
                                if (!displayAccount.isNullOrBlank()) {
                                    Spacer(Modifier.size(3.dp))
                                    Text(
                                        displayAccount,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KeybookColors.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                }

                if (mode == Mode.INVALID || !repository.isInitialized()) {
                    Text(
                        "请先打开密码本，设置主密码并创建一个保险箱。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KeybookColors.TextMuted,
                    )
                    Spacer(Modifier.size(18.dp))
                    PrimaryButton(
                        text = "打开密码本",
                        onClick = {
                            startActivity(Intent(context, com.keybook.vault.MainActivity::class.java))
                            finish()
                        },
                    )
                } else if (!unlocked) {
                    Text(
                        "解锁保险箱后才能继续",
                        style = MaterialTheme.typography.labelSmall,
                        color = KeybookColors.TextFaint,
                    )
                    Spacer(Modifier.size(8.dp))
                    KeybookTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
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
                    Spacer(Modifier.size(12.dp))
                    PrimaryButton(
                        text = "解锁保险箱",
                        enabled = password.isNotBlank(),
                        loading = busy,
                        onClick = { unlockWithPassword() },
                    )
                    if (security.biometricEnabled && security.biometricAvailable) {
                        Spacer(Modifier.size(12.dp))
                        SecondaryButton(
                            text = if (promptVisible) "等待指纹验证…" else "使用指纹解锁",
                            icon = Icons.Rounded.Fingerprint,
                            enabled = !promptVisible && !busy,
                            onClick = { promptBiometric() },
                        )
                    }
                } else if (mode == Mode.UNLOCK) {
                    if (error != null) {
                        Spacer(Modifier.size(18.dp))
                        SecondaryButton(
                            text = "关闭",
                            onClick = { cancelFlow() },
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = KeybookColors.Mint, strokeWidth = 2.dp)
                        }
                    }
                } else {
                    Text(
                        if (mode == Mode.CREATE) {
                            "将生成 ES256 密钥对，并加密保存在这台设备上。"
                        } else {
                            "签名只会在本机完成，不会把私钥交给请求应用。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = KeybookColors.TextMuted,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.size(18.dp))
                    PrimaryButton(
                        text = if (mode == Mode.CREATE) "保存到密码本" else "确认并登录",
                        enabled = !busy,
                        loading = busy,
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                val message = if (mode == Mode.CREATE) completeCreate() else completeGet()
                                if (message != null) {
                                    error = message
                                    busy = false
                                }
                            }
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    SecondaryButton(
                        text = "取消",
                        enabled = !busy,
                        onClick = { cancelFlow() },
                    )
                }

                if (error != null) {
                    Spacer(Modifier.size(12.dp))
                    InlineError(error.orEmpty())
                }

                Spacer(Modifier.size(18.dp))
                Text(
                    if (callingLabel.isBlank()) {
                        "来自其他应用的请求"
                    } else {
                        "请求来自 $callingLabel"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = KeybookColors.TextFaint,
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

