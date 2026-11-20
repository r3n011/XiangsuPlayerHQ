package com.theveloper.pixelplay.presentation.tvauth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.bilibili.BilibiliRepository
import com.theveloper.pixelplay.data.kugou.KugouRepository
import com.theveloper.pixelplay.data.netease.NeteaseRepository
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import com.theveloper.pixelplay.data.tvauth.TvAuthClient
import com.theveloper.pixelplay.data.tvauth.TvAuthPayload
import com.theveloper.pixelplay.data.tvauth.TvAuthorizeAccounts
import com.theveloper.pixelplay.data.tvauth.TvQrPayload
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * 扫描电视上的二维码，把手机已登录账号的 Cookie 通过局域网传给电视。
 */
@AndroidEntryPoint
class TvAuthorizeActivity : ComponentActivity() {

    @Inject lateinit var neteaseRepository: NeteaseRepository
    @Inject lateinit var qqMusicRepository: QqMusicRepository
    @Inject lateinit var kugouRepository: KugouRepository
    @Inject lateinit var bilibiliRepository: BilibiliRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) {
                        TvAuthorizeScreen(
                            loadAccounts = ::loadAccounts,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun loadAccounts(): TvAuthorizeAccounts {
        val netease = runCatching { neteaseRepository.getCookieString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseCookieHeader)
            ?.takeIf { it.containsKey("MUSIC_U") }
        val qq = if (qqMusicRepository.isLoggedIn) {
            runCatching { qqMusicRepository.getCookieMap() }.getOrNull()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val bilibili = runCatching { bilibiliRepository.getCookieString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseCookieJson)
            ?.takeIf { it.containsKey("SESSDATA") || it.containsKey("bili_jct") }
        val kugou = runCatching { kugouRepository.getCookieString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseCookieHeader)
        return TvAuthorizeAccounts(
            netease = netease,
            qq = qq,
            bilibili = bilibili,
            kugouToken = kugou?.get("token"),
            kugouUserId = kugou?.get("userid"),
            kugouVipToken = kugou?.get("vip_token"),
        )
    }
}

private enum class Step { Scanning, Confirm, Sending, Success, Error }

@Composable
private fun TvAuthorizeScreen(
    loadAccounts: () -> TvAuthorizeAccounts,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Scanning) }
    var scanAttempt by remember { mutableIntStateOf(0) }
    var scanHint by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<TvQrPayload?>(null) }
    var accounts by remember { mutableStateOf<TvAuthorizeAccounts?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            errorMessage = "需要相机权限才能扫描电视上的二维码"
            step = Step.Error
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(step) {
        if (step == Step.Success) {
            delay(1_400)
            onClose()
        }
    }
    BackHandler {
        if (step == Step.Scanning) onClose() else {
            step = Step.Scanning
            scanAttempt++
            errorMessage = null
        }
    }

    when (step) {
        Step.Scanning -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (cameraGranted) {
                key(scanAttempt) {
                    QrScannerScreen(
                        modifier = Modifier.fillMaxSize(),
                        onPayload = { raw ->
                            val payload = TvQrPayload.parse(raw)
                            if (payload == null) {
                                scanHint = "这不是电视授权二维码，请对准电视屏幕"
                                scanAttempt++
                            } else {
                                val loaded = loadAccounts()
                                if (loaded.isEmpty) {
                                    errorMessage = "手机还没有登录任何音乐账号，请先登录后再试"
                                    step = Step.Error
                                } else {
                                    target = payload
                                    accounts = loaded
                                    selected = loaded.availableKeys.toSet()
                                    scanHint = null
                                    step = Step.Confirm
                                }
                            }
                        },
                        onCameraError = { message ->
                            errorMessage = message
                            step = Step.Error
                        },
                    )
                }
                ScanOverlay(
                    hint = scanHint ?: "对准电视上的二维码",
                    onClose = onClose,
                )
            } else {
                PermissionRequest(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    },
                    onClose = onClose,
                )
            }
        }

        Step.Confirm -> {
            val payload = target
            val current = accounts
            if (payload == null || current == null) {
                LaunchedEffect(Unit) { step = Step.Scanning }
                return
            }
            ConfirmScreen(
                target = payload,
                accounts = current,
                selected = selected,
                onToggle = { key ->
                    selected = if (key in selected) selected - key else selected + key
                },
                onSend = {
                    if (selected.isNotEmpty()) {
                        step = Step.Sending
                        scope.launch {
                            val body = TvAuthPayload.build(current, selected)
                            TvAuthClient().send(payload, body)
                                .onSuccess { step = Step.Success }
                                .onFailure {
                                    errorMessage = it.message ?: "发送失败"
                                    step = Step.Error
                                }
                        }
                    }
                },
                onCancel = onClose,
            )
        }

        Step.Sending -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = "正在发送登录信息…",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        Step.Success -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已发送到电视", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "请在电视屏幕上确认登录结果，本页即将关闭",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, start = 32.dp, end = 32.dp),
                )
            }
        }

        Step.Error -> ErrorScreen(
            message = errorMessage ?: "操作失败",
            onRetry = {
                errorMessage = null
                scanHint = null
                step = Step.Scanning
                scanAttempt++
                if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onClose = onClose,
        )
    }
}

@Composable
private fun ScanOverlay(hint: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp)),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 20.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(hint, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onClose) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun ConfirmScreen(
    target: TvQrPayload,
    accounts: TvAuthorizeAccounts,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("授权电视登录", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "将向 ${target.host} 的电视发送以下账号的登录信息",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            accounts.availableKeys.forEach { key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = key in selected,
                        onCheckedChange = { onToggle(key) },
                    )
                    Text(
                        text = accountLabel(key),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        Text(
            "提示：请确认手机与电视连接在同一个 Wi-Fi。登录信息只会发送到电视，不会上传到任何服务器。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSend,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("发送到电视")
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("无法完成授权", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("重新扫码") }
                OutlinedButton(onClick = onClose) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("需要相机权限", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "扫描电视上的二维码需要使用相机。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRequest) { Text("授予相机权限") }
            OutlinedButton(onClick = onOpenSettings) { Text("打开系统设置") }
            OutlinedButton(onClick = onClose) { Text("关闭") }
        }
    }
}

private fun accountLabel(key: String): String = when (key) {
    TvAuthorizeAccounts.KEY_NETEASE -> "网易云音乐"
    TvAuthorizeAccounts.KEY_QQ -> "QQ 音乐"
    TvAuthorizeAccounts.KEY_BILIBILI -> "Bilibili"
    TvAuthorizeAccounts.KEY_KUGOU -> "酷狗音乐"
    else -> key
}

private fun parseCookieHeader(value: String): Map<String, String> =
    value.split(';')
        .mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null
            else part.substring(0, index).trim() to part.substring(index + 1).trim()
        }
        .filter { it.first.isNotEmpty() }
        .toMap()

private fun parseCookieJson(value: String): Map<String, String>? = runCatching {
    val json = JSONObject(value)
    buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, json.optString(key))
        }
    }
}.getOrNull()
