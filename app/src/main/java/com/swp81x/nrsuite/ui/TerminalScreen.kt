package com.swp81x.nrsuite.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swp81x.nrsuite.core.terminal.DEFAULT_TERMINAL_HOST
import com.swp81x.nrsuite.core.terminal.DEFAULT_TERMINAL_PORT
import com.swp81x.nrsuite.core.terminal.TERMUX_PACKAGE_NAME
import com.swp81x.nrsuite.core.terminal.TerminalConfig
import com.swp81x.nrsuite.core.terminal.TerminalOutputSnapshot
import com.swp81x.nrsuite.core.terminal.TerminalConnectionState
import com.swp81x.nrsuite.ui.components.NrDangerButton
import com.swp81x.nrsuite.ui.components.NrPrimaryButton
import com.swp81x.nrsuite.ui.components.NrQuietButton
import com.swp81x.nrsuite.ui.components.NrSecondaryButton
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrTheme
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

private const val TERMUX_FDROID_URL = "https://f-droid.org/packages/com.termux/"
private const val TERMUX_GITHUB_URL = "https://github.com/termux/termux-app"

private val terminalSetupCommands = """
pkg install openssh
passwd
sshd
whoami
""".trimIndent()

@Composable
internal fun TerminalScreen(
    connectionState: TerminalConnectionState,
    terminalOutput: Flow<TerminalOutputSnapshot>,
    initialHost: String,
    initialPort: Int,
    initialUsername: String,
    onConnect: (TerminalConfig) -> Unit,
    onDisconnect: () -> Unit,
    onSendInput: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var host by rememberSaveable { mutableStateOf(initialHost) }
    var port by rememberSaveable { mutableStateOf(initialPort.toString()) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var refreshCount by remember { mutableIntStateOf(0) }
    val termuxInstalled = remember(refreshCount) {
        runCatching {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
        }.isSuccess
    }

    when (connectionState) {
        is TerminalConnectionState.Connected -> {
            ConnectedTerminal(
                state = connectionState,
                terminalOutput = terminalOutput,
                onDisconnect = onDisconnect,
                onSendInput = onSendInput,
                onResize = onResize,
                modifier = modifier,
            )
        }
        else -> {
            TerminalSetup(
                state = connectionState,
                host = host,
                onHostChange = { host = it },
                port = port,
                onPortChange = { value -> port = value.filter(Char::isDigit).take(5) },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                termuxInstalled = termuxInstalled,
                onRefreshTermux = { refreshCount++ },
                onConnect = {
                    onConnect(
                        TerminalConfig(
                            host = host,
                            port = port.toIntOrNull() ?: DEFAULT_TERMINAL_PORT,
                            username = username,
                            password = password,
                        ),
                    )
                },
                onOpenTermux = {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
                    if (intent != null) {
                        context.startActivity(
                            Intent.createChooser(intent, "Open Termux"),
                        )
                    } else {
                        openBrowser(context, TERMUX_FDROID_URL)
                    }
                },
                onInstallTermux = { openBrowser(context, TERMUX_FDROID_URL) },
                onCopySetup = {
                    clipboard.setText(AnnotatedString(terminalSetupCommands))
                },
                onOpenTermuxProject = { openBrowser(context, TERMUX_GITHUB_URL) },
                onClearPinnedHostKey = onClearPinnedHostKey,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TerminalSetup(
    state: TerminalConnectionState,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    termuxInstalled: Boolean,
    onRefreshTermux: () -> Unit,
    onConnect: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onCopySetup: () -> Unit,
    onOpenTermuxProject: () -> Unit,
    onClearPinnedHostKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Termux SSH terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (termuxInstalled) {
                                "Termux is installed on this device."
                            } else {
                                "Termux is required as the local Linux runtime."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NrQuietButton(
                        text = "Recheck",
                        onClick = onRefreshTermux,
                        leadingIcon = Icons.Default.Refresh,
                        compact = true,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (termuxInstalled) {
                        NrSecondaryButton(
                            text = "Open Termux",
                            onClick = onOpenTermux,
                            leadingIcon = Icons.Default.OpenInNew,
                            compact = true,
                        )
                    } else {
                        NrPrimaryButton(
                            text = "Get Termux",
                            onClick = onInstallTermux,
                            leadingIcon = Icons.Default.Link,
                            compact = true,
                        )
                    }
                    NrQuietButton(
                        text = "Copy setup",
                        onClick = onCopySetup,
                        leadingIcon = Icons.Default.ContentCopy,
                        compact = true,
                    )
                }

                Text(
                    text = "In Termux run:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = terminalSetupCommands,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                )
                Text(
                    text = "Use whoami in Termux for the username. SSH listens on localhost:8022.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenTermuxProject) {
                    Text("Termux source / project")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Termux username") },
                    placeholder = { Text("Output of whoami") },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Termux password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = onHostChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Host") },
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                if (state is TerminalConnectionState.Connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Connecting to Termux SSH…",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
                if (state is TerminalConnectionState.Failed) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
                if (state is TerminalConnectionState.Disconnected &&
                    !state.message.isNullOrBlank()
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                NrPrimaryButton(
                    text = "Connect to Termux",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank() && password.isNotEmpty() &&
                        host.isNotBlank() && port.isNotBlank() &&
                        state !is TerminalConnectionState.Connecting,
                )
                Text(
                    text = "The password is kept in memory for this session only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NrQuietButton(
                    text = "Clear pinned SSH host key",
                    onClick = onClearPinnedHostKey,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun ConnectedTerminal(
    state: TerminalConnectionState.Connected,
    terminalOutput: Flow<TerminalOutputSnapshot>,
    onDisconnect: () -> Unit,
    onSendInput: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webReady by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val nrColors = NrTheme.colors
    val terminalThemeJson = remember(colorScheme, nrColors) {
        JSONObject().apply {
            put("background", colorScheme.background.toCssHex())
            put("foreground", colorScheme.onSurface.toCssHex())
            put("cursor", colorScheme.primary.toCssHex())
            put("cursorAccent", colorScheme.background.toCssHex())
            put("selectionBackground", colorScheme.primary.copy(alpha = 0.35f).toCssHex())
            put("black", colorScheme.surface.toCssHex())
            put("red", colorScheme.error.toCssHex())
            put("green", nrColors.statusGreen.toCssHex())
            put("yellow", nrColors.statusAmber.toCssHex())
            put("blue", nrColors.statusInfo.toCssHex())
            put("magenta", colorScheme.secondary.toCssHex())
            put("cyan", colorScheme.tertiary.toCssHex())
            put("white", colorScheme.onSurface.toCssHex())
            put("brightBlack", colorScheme.onSurfaceVariant.toCssHex())
            put("brightRed", colorScheme.error.toCssHex())
            put("brightGreen", nrColors.statusGreen.toCssHex())
            put("brightYellow", nrColors.statusAmber.toCssHex())
            put("brightBlue", nrColors.statusInfo.toCssHex())
            put("brightMagenta", colorScheme.secondary.toCssHex())
            put("brightCyan", colorScheme.tertiary.toCssHex())
            put("brightWhite", colorScheme.onSurface.toCssHex())
        }.toString()
    }

    val focusTerminal: () -> Unit = {
        webView?.post {
            webView?.requestFocus()
            webView?.evaluateJavascript("terminalFocus()", null)
        }
    }
    val hideKeyboard: () -> Unit = {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(webView?.windowToken, 0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(StatusGreen, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "${state.username}@${state.host}:${state.port}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            NrQuietButton(
                text = "Keyboard",
                onClick = hideKeyboard,
                leadingIcon = Icons.Default.KeyboardArrowDown,
                compact = true,
            )
            Spacer(Modifier.width(6.dp))
            NrDangerButton(
                text = "Disconnect",
                onClick = onDisconnect,
                compact = true,
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colorScheme.background),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowContentAccess = false
                    settings.allowFileAccess = true
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            webReady = true
                            view?.evaluateJavascript("terminalFit()", null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?,
                        ): Boolean = true
                    }
                    addJavascriptInterface(
                        TerminalWebBridge(
                            onInput = onSendInput,
                            onResize = onResize,
                            onReady = {
                                webReady = true
                            },
                        ),
                        "NrTerminal",
                    )
                    loadUrl("file:///android_asset/terminal/index.html")
                    webView = this
                }
            },
            update = { view ->
                webView = view
            },
        )

        LaunchedEffect(webView, webReady, terminalThemeJson) {
            if (webReady) {
                webView?.evaluateJavascript("setTerminalTheme($terminalThemeJson)", null)
            }
        }
        var consumedGeneration by remember { mutableStateOf(-1L) }
        var consumedBytes by remember { mutableStateOf(0) }
        LaunchedEffect(webView, webReady, terminalOutput) {
            if (!webReady) return@LaunchedEffect
            terminalOutput.collect { snapshot ->
                val writeChunk: ByteArray? = if (snapshot.generation != consumedGeneration) {
                    consumedGeneration = snapshot.generation
                    consumedBytes = snapshot.bytes.size
                    webView?.post {
                        webView?.evaluateJavascript("terminalReset()", null)
                    }
                    snapshot.bytes.takeIf { it.isNotEmpty() }
                } else if (snapshot.bytes.size > consumedBytes) {
                    val chunk = snapshot.bytes.copyOfRange(consumedBytes, snapshot.bytes.size)
                    consumedBytes = snapshot.bytes.size
                    chunk
                } else {
                    null
                }
                writeChunk?.let { bytes ->
                    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    webView?.post {
                        webView?.evaluateJavascript(
                            "terminalWriteBase64('$encoded')",
                            null,
                        )
                    }
                }
            }
        }

        TerminalKeyRow(
            onKey = { value ->
                onSendInput(value)
                focusTerminal()
            },
        )
    }

    DisposableEffect(webView) {
        onDispose {
            webView?.removeJavascriptInterface("NrTerminal")
            webView?.destroy()
        }
    }
}

@Composable
private fun TerminalKeyRow(
    onKey: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val keys = listOf(
            "ESC" to "\u001b",
            "CTRL-C" to "\u0003",
            "CTRL-D" to "\u0004",
            "CTRL-L" to "\u000c",
            "TAB" to "\t",
            "↑" to "\u001b[A",
            "↓" to "\u001b[B",
            "→" to "\u001b[C",
            "←" to "\u001b[D",
            "|" to "|",
            "~" to "~",
            "/" to "/",
            "-" to "-",
            "_" to "_",
        )
        keys.forEach { (label, sequence) ->
            TextButton(
                onClick = { onKey(sequence) },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

private class TerminalWebBridge(
    private val onInput: (String) -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onReady: () -> Unit,
) {
    @JavascriptInterface
    fun sendInput(data: String) {
        onInput(data)
    }

    @JavascriptInterface
    fun resize(columns: Int, rows: Int) {
        onResize(columns, rows)
    }

    @JavascriptInterface
    fun ready() {
        onReady()
    }
}

private fun openBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun Color.toCssHex(): String {
    val argb = toArgb()
    val rgb = argb and 0xFFFFFF
    val alpha = (argb ushr 24) and 0xFF
    return if (alpha == 0xFF) {
        String.format("#%06X", rgb)
    } else {
        String.format("#%06X%02X", rgb, alpha)
    }
}
