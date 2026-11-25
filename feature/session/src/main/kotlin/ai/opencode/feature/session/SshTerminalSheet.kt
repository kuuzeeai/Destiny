package ai.opencode.feature.session

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.DialogWindowProvider
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import kotlinx.coroutines.delay
import android.os.SystemClock
import ai.opencode.core.ui.OpenCodeFonts
import ai.opencode.core.ui.OpenCodeTextStyles
import ai.opencode.core.ui.OpenCodeTheme
import ai.opencode.feature.composer.OpenCodeBottomSheet
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

/*
 * Mi terminal (SSH): tu propia terminal al celular donde corren Termux y el servidor OpenCode.
 * No pasa por el agente (no gasta tokens). La primera vez entra con contraseña (que no se guarda),
 * crea una llave propia, la guarda cifrada con el Keystore de Android y la copia al celular viejo;
 * desde entonces entra sola. La huella del servidor se confirma una vez y se vigila siempre.
 * Terminal simple (TERM=dumb): sin programas de pantalla completa (vim, nano, htop).
 */

private const val SSH_PREFS = "destiny_ssh"
private const val SSH_ALIAS = "destiny_ssh_aes"
private const val SSH_MAX_LINES = 3000
private const val SSH_DEFAULT_HOST = "192.168.101.214"
private const val SSH_DEFAULT_PORT = 8022
private const val SSH_DEFAULT_USER = "u0_a264"

private val sshOsc = Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")
private val sshCsi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val sshCharset = Regex("\u001B[()][A-Za-z0-9]|\u001B[=>]")
private val sshIncomplete = Regex("\u001B(\\[[0-9;?]*[ -/]*|\\][^\u0007\u001B]*)?$")

private val SSH_FG = 0xFFFFFFFF.toInt()
private val SSH_ANSI = intArrayOf(
    0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
    0xFF6495ED.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
    0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
    0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt(),
)
private val sshCsiParts = Regex("\u001B\\[([0-9;?<=>]*)([ -/]*)([@-~])")

private fun ssh256(value: Int): Int {
    val i = value.coerceIn(0, 255)
    if (i < 16) return SSH_ANSI[i]
    if (i < 232) {
        val v = i - 16
        val r = v / 36
        val g = (v / 6) % 6
        val b = v % 6
        val lr = if (r == 0) 0 else 55 + r * 40
        val lg = if (g == 0) 0 else 55 + g * 40
        val lb = if (b == 0) 0 else 55 + b * 40
        return (0xFF shl 24) or (lr shl 16) or (lg shl 8) or lb
    }
    val gray = 8 + (i - 232) * 10
    return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
}

private fun styled(sb: CharSequence, colors: List<Int>, cursor: Int): AnnotatedString {
    val length = maxOf(sb.length, cursor + 1)
    if (length <= 0) return AnnotatedString("")
    return buildAnnotatedString {
        var start = 0
        while (start < length) {
            if (start == cursor) {
                pushStyle(SpanStyle(color = Color.Black, background = Color.White))
                append(if (start < sb.length) sb[start].toString() else " ")
                pop()
                start++
                continue
            }
            val color = if (start < colors.size) colors[start] else SSH_FG
            var end = start + 1
            while (end < length && end != cursor && (if (end < colors.size) colors[end] else SSH_FG) == color) end++
            pushStyle(SpanStyle(color = Color(color)))
            append(buildString { for (k in start until end) append(if (k < sb.length) sb[k] else ' ') })
            pop()
            start = end
        }
    }
}

private class SshStore(context: Context) {
    private val prefs = context.getSharedPreferences(SSH_PREFS, Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) { prefs.edit().putString("host", value).apply() }

    var port: Int
        get() = prefs.getInt("port", SSH_DEFAULT_PORT)
        set(value) { prefs.edit().putInt("port", value).apply() }

    var user: String
        get() = prefs.getString("user", "") ?: ""
        set(value) { prefs.edit().putString("user", value).apply() }

    var knownHosts: String
        get() = prefs.getString("known_hosts", "") ?: ""
        set(value) { prefs.edit().putString("known_hosts", value).apply() }

    val hasKey: Boolean get() = prefs.contains("key")

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(SSH_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(SSH_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun saveKey(plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        val text = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString("key", text).apply()
    }

    fun loadKey(): ByteArray? {
        val text = prefs.getString("key", null) ?: return null
        return runCatching {
            val parts = text.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        }.getOrNull()
    }

    fun forget() {
        prefs.edit().remove("key").remove("known_hosts").apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(SSH_ALIAS)
        }
    }
}

private enum class SshPhase { Form, Connecting, Connected }

@Stable
private class SshShell(
    private val context: Context,
    private val store: SshStore,
    private val scope: CoroutineScope,
) {
    var phase by mutableStateOf(SshPhase.Form)
    var error by mutableStateOf<String?>(null)
    var hostPrompt by mutableStateOf<String?>(null)
    var hasKey by mutableStateOf(store.hasKey)
    var hasKnownHosts by mutableStateOf(store.knownHosts.isNotBlank())
    val lines = mutableStateListOf<AnnotatedString>()
    var current by mutableStateOf(AnnotatedString(""))

    val savedHost: String get() = store.host.ifBlank { SSH_DEFAULT_HOST }
    val savedPort: Int get() = if (store.host.isBlank()) SSH_DEFAULT_PORT else store.port
    val savedUser: String get() = store.user.ifBlank { SSH_DEFAULT_USER }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var output: OutputStream? = null
    private var trust: CompletableDeferred<Boolean>? = null
    private var userClosed = false
    private val writer by lazy { Executors.newSingleThreadExecutor() }
    private val edit = StringBuilder()
    private val editColors = ArrayList<Int>()
    private var curColor = SSH_FG
    private var cursor = 0
    private var pendingEscape = ""

    /** Si ya hay datos y llave guardada, entra sola al abrir. */
    fun autoConnect() {
        if (phase != SshPhase.Form || store.host.isBlank() || store.user.isBlank()) return
        if (store.loadKey() == null) return
        connect(store.host, store.port, store.user, "")
    }

    fun connect(host: String, port: Int, user: String, password: String) {
        if (phase == SshPhase.Connecting) return
        phase = SshPhase.Connecting
        error = null
        userClosed = false
        store.host = host
        store.port = port
        store.user = user
        scope.launch(Dispatchers.IO) {
            try {
                runSession(host, port, user, password)
            } catch (t: Throwable) {
                closeQuietly()
                if (!userClosed) {
                    val message = explain(t)
                    scope.launch {
                        hostPrompt = null
                        phase = SshPhase.Form
                        error = message
                    }
                }
            }
        }
    }

    private fun runSession(host: String, port: Int, user: String, password: String) {
        JSch.setConfig("CheckKexes", "curve25519-sha256,curve25519-sha256@libssh.org,curve448-sha512")
        JSch.setConfig("CheckSignatures", "ssh-ed25519,ssh-ed448")
        val jsch = JSch()
        val known = store.knownHosts
        if (known.isNotBlank()) jsch.setKnownHosts(ByteArrayInputStream(known.toByteArray(Charsets.UTF_8)))
        val key = if (password.isEmpty()) store.loadKey() else null
        if (password.isEmpty() && key == null) throw IllegalStateException(context.getString(R.string.ssh_err_no_key))
        if (key != null) jsch.addIdentity("destiny", key, null, null)
        val s = jsch.getSession(user, host, port)
        if (password.isNotEmpty()) s.setPassword(password)
        s.setConfig("PreferredAuthentications", if (password.isNotEmpty()) "password,keyboard-interactive" else "publickey")
        s.setConfig("StrictHostKeyChecking", "ask")
        s.setUserInfo(AskInfo())
        s.setServerAliveInterval(30000)
        s.connect(15000)
        session = s

        // La huella confirmada se guarda para vigilarla en las proximas conexiones.
        val keys = jsch.hostKeyRepository.hostKey
        if (keys != null && keys.isNotEmpty()) {
            store.knownHosts = keys.joinToString("\n") { "${it.host} ${it.type} ${it.key}" }
        }
        val note = if (password.isNotEmpty()) installKey(s) else null

        val shell = s.openChannel("shell") as ChannelShell
        shell.setPtyType("xterm-256color", 80, 24, 0, 0)
        val input = shell.inputStream
        output = shell.outputStream
        shell.connect(10000)
        channel = shell
        scope.launch {
            lines.clear()
            edit.setLength(0)
            editColors.clear()
            curColor = SSH_FG
            cursor = 0
            current = AnnotatedString("")
            if (note != null) lines.add(AnnotatedString(note))
            hasKey = store.hasKey
            hasKnownHosts = store.knownHosts.isNotBlank()
            phase = SshPhase.Connected
        }

        val reader = InputStreamReader(input, Charsets.UTF_8)
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            val chunk = String(buffer, 0, count)
            scope.launch { feed(chunk) }
        }
        closeQuietly()
        if (!userClosed) {
            val message = context.getString(R.string.ssh_err_closed)
            scope.launch {
                phase = SshPhase.Form
                error = message
            }
        }
    }

    /** Crea una llave propia y la agrega a authorized_keys del celular viejo. Devuelve una nota para mostrar. */
    private fun installKey(s: Session): String {
        return try {
            val pair = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
            val priv = ByteArrayOutputStream()
            pair.writePrivateKey(priv)
            val pub = ByteArrayOutputStream()
            pair.writePublicKey(pub, "destiny-app")
            pair.dispose()
            val pubLine = pub.toString("UTF-8").trim()
            val blob = pubLine.split(" ").getOrNull(1) ?: throw IllegalStateException("llave publica vacia")
            val command = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && " +
                "chmod 600 ~/.ssh/authorized_keys && " +
                "(grep -qF '$blob' ~/.ssh/authorized_keys || echo '$pubLine' >> ~/.ssh/authorized_keys)"
            val exec = s.openChannel("exec") as ChannelExec
            exec.setCommand(command)
            exec.connect(10000)
            val start = System.currentTimeMillis()
            while (!exec.isClosed && System.currentTimeMillis() - start < 10000) Thread.sleep(100)
            val code = exec.exitStatus
            exec.disconnect()
            if (code == 0) {
                store.saveKey(priv.toByteArray())
                context.getString(R.string.ssh_key_saved)
            } else {
                context.getString(R.string.ssh_key_failed)
            }
        } catch (t: Throwable) {
            context.getString(R.string.ssh_key_failed)
        }
    }

    private inner class AskInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?): Boolean = false
        override fun promptPassphrase(message: String?): Boolean = false
        override fun showMessage(message: String?) {}
        override fun promptYesNo(message: String?): Boolean {
            val text = message.orEmpty()
            // Huella distinta a la guardada: nunca se acepta sola.
            if (text.contains("HAS CHANGED", ignoreCase = true)) return false
            val answer = CompletableDeferred<Boolean>()
            trust = answer
            scope.launch { hostPrompt = text }
            return runBlocking { answer.await() }
        }
    }

    fun confirmTrust(ok: Boolean) {
        hostPrompt = null
        trust?.complete(ok)
    }

    private fun explain(t: Throwable): String {
        val m = t.message.orEmpty()
        return when {
            m.contains("Auth fail", true) || m.contains("Auth cancel", true) -> context.getString(R.string.ssh_err_auth)
            m.contains("has been changed", true) -> context.getString(R.string.ssh_err_changed)
            m.contains("reject HostKey", true) -> context.getString(R.string.ssh_err_rejected)
            t is IllegalStateException -> m
            t is java.net.UnknownHostException || t is java.net.ConnectException ||
                m.contains("timeout", true) || m.contains("refused", true) ||
                m.contains("socket is not established", true) || m.contains("Network is unreachable", true) ->
                context.getString(R.string.ssh_err_network)
            else -> context.getString(R.string.ssh_err_generic, m.take(160))
        }
    }

    fun feed(chunk: String) {
        var text = (pendingEscape + chunk).replace(sshOsc, "").replace(sshCharset, "")
        val tail = sshIncomplete.find(text)
        if (tail != null) {
            pendingEscape = tail.value.take(256)
            text = text.substring(0, tail.range.first)
        } else {
            pendingEscape = ""
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\u001B') {
                val m = sshCsiParts.matchAt(text, i)
                if (m != null) {
                    csi(m.groupValues[1], m.groupValues[3][0])
                    i = m.range.last + 1
                } else {
                    i++
                }
                continue
            }
            when (c) {
                '\n' -> {
                    lines.add(styled(edit, editColors, -1))
                    edit.setLength(0)
                    editColors.clear()
                    cursor = 0
                }
                '\r' -> cursor = 0
                '\b' -> if (cursor > 0) cursor--
                '\t' -> repeat(8 - cursor % 8) { put(' ') }
                else -> if (c >= ' ') put(c)
            }
            i++
        }
        if (lines.size > SSH_MAX_LINES) lines.subList(0, 500).clear()
        current = styled(edit, editColors, cursor)
    }

    private fun csi(params: String, fin: Char) {
        val nums = params.trimStart('?', '<', '=', '>').split(';').map { it.toIntOrNull() }
        val first = nums.firstOrNull()
        val count = (first ?: 1).coerceAtLeast(1)
        when (fin) {
            'm' -> sgr(nums.map { it ?: 0 })
            'K' -> when (first ?: 0) {
                0 -> if (cursor < edit.length) {
                    edit.setLength(cursor)
                    while (editColors.size > cursor) editColors.removeAt(editColors.size - 1)
                }
                1 -> for (k in 0 until minOf(cursor + 1, edit.length)) edit.setCharAt(k, ' ')
                else -> {
                    edit.setLength(0)
                    editColors.clear()
                }
            }
            'C' -> cursor += count
            'D' -> cursor = maxOf(0, cursor - count)
            'G' -> cursor = count - 1
            'P' -> {
                val end = minOf(edit.length, cursor + count)
                if (cursor < end) {
                    edit.delete(cursor, end)
                    repeat(end - cursor) { editColors.removeAt(cursor) }
                }
            }
            '@' -> {
                pad(cursor)
                edit.insert(cursor, " ".repeat(count))
                repeat(count) { editColors.add(cursor, curColor) }
            }
        }
    }

    private fun sgr(codes: List<Int>) {
        val list = if (codes.isEmpty()) listOf(0) else codes
        var i = 0
        while (i < list.size) {
            val code = list[i]
            when {
                code == 0 || code == 39 -> curColor = SSH_FG
                code in 30..37 -> curColor = SSH_ANSI[code - 30]
                code in 90..97 -> curColor = SSH_ANSI[code - 90 + 8]
                code == 38 || code == 48 -> {
                    val mode = list.getOrNull(i + 1)
                    if (mode == 5) {
                        if (code == 38) curColor = ssh256(list.getOrNull(i + 2) ?: 0)
                        i += 2
                    } else if (mode == 2) {
                        val r = (list.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                        val g = (list.getOrNull(i + 3) ?: 0).coerceIn(0, 255)
                        val b = (list.getOrNull(i + 4) ?: 0).coerceIn(0, 255)
                        if (code == 38) curColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun pad(pos: Int) {
        while (edit.length < pos) {
            edit.append(' ')
            editColors.add(curColor)
        }
    }

    private fun put(c: Char) {
        pad(cursor)
        if (cursor < edit.length) {
            edit.setCharAt(cursor, c)
            editColors[cursor] = curColor
        } else {
            edit.append(c)
            editColors.add(curColor)
        }
        cursor++
    }

    fun resize(cols: Int, rows: Int) {
        val ch = channel ?: return
        runCatching { writer.execute { runCatching { ch.setPtySize(cols, rows, 0, 0) } } }
    }

    fun send(text: String) {
        val out = output ?: return
        writer.execute {
            runCatching {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }

    fun clear() {
        lines.clear()
    }

    fun forget() {
        store.forget()
        hasKey = false
        hasKnownHosts = false
        error = null
    }

    /** Cierra la conexion (en otro hilo: la red no va en el hilo principal). */
    fun disconnect() {
        userClosed = true
        trust?.complete(false)
        thread { closeQuietly() }
        runCatching { writer.shutdown() }
    }

    fun leave() {
        disconnect()
        hostPrompt = null
        error = null
        phase = SshPhase.Form
    }

    private fun closeQuietly() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
        output = null
    }
}

@Composable
fun SshTerminalSheet(onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val shell = remember { SshShell(context, SshStore(context), scope) }
    DisposableEffect(shell) { onDispose { shell.disconnect() } }
    LaunchedEffect(shell) { shell.autoConnect() }
    var lastBack by remember { mutableStateOf(0L) }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(lastBack) {
        if (lastBack > 0L) {
            hint = true
            delay(2000)
            hint = false
        }
    }
    val connected = shell.phase == SshPhase.Connected && shell.hostPrompt == null
    val background = if (connected) Color.Black else MaterialTheme.colorScheme.surface
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (connected || darkTheme) 0 else mask, mask)
        }
    }
    Dialog(
        onDismissRequest = {
            val now = SystemClock.elapsedRealtime()
            if (lastBack > 0L && now - lastBack < 2000L) onClose() else lastBack = now
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = if (connected) 0.dp else 4.dp)
                    .padding(bottom = if (connected) 0.dp else 4.dp),
            ) {
                if (!connected) {
                    Text(
                        stringResource(R.string.ssh_title),
                        style = OpenCodeTextStyles.callout.copy(fontWeight = FontWeight.SemiBold),
                        color = OpenCodeTheme.colors.text,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                val prompt = shell.hostPrompt
                if (prompt != null) {
                    SshHostPrompt(prompt, onAnswer = shell::confirmTrust)
                } else when (shell.phase) {
                    SshPhase.Connected -> SshTerminalPane(shell)
                    SshPhase.Connecting -> Text(
                        stringResource(R.string.ssh_connecting),
                        color = OpenCodeTheme.colors.text,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    SshPhase.Form -> SshConnectForm(shell)
                }
            }
            if (hint) {
                Text(
                    stringResource(R.string.ssh_back_hint),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .padding(bottom = 150.dp)
                        .background(Color(0xE6000000), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SshHostPrompt(message: String, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.ssh_trust_title),
            style = OpenCodeTextStyles.callout.copy(fontWeight = FontWeight.SemiBold),
            color = OpenCodeTheme.colors.text,
        )
        Text(
            message,
            color = OpenCodeTheme.colors.text,
            fontFamily = OpenCodeFonts.monospace,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAnswer(true) }) { Text(stringResource(R.string.ssh_trust_yes)) }
            TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.ssh_trust_no)) }
        }
    }
}

@Composable
private fun SshConnectForm(shell: SshShell) {
    var host by rememberSaveable { mutableStateOf(shell.savedHost) }
    var port by rememberSaveable { mutableStateOf(shell.savedPort.toString()) }
    var user by rememberSaveable { mutableStateOf(shell.savedUser) }
    // La contraseña solo vive aqui, en memoria: no se guarda ni se restaura.
    var password by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shell.error?.let { Text(it, color = Color(0xFFFF7B72), fontSize = 13.sp) }
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.ssh_host)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.ssh_port)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text(stringResource(R.string.ssh_user)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.ssh_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (shell.hasKey) {
            Text(stringResource(R.string.ssh_key_hint), color = OpenCodeTheme.colors.text, fontSize = 13.sp)
        }
        Button(
            onClick = {
                val typed = password
                password = ""
                shell.connect(host.trim(), port.toIntOrNull() ?: SSH_DEFAULT_PORT, user.trim(), typed)
            },
            enabled = host.isNotBlank() && user.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.ssh_connect)) }
        if (shell.hasKey || shell.hasKnownHosts) {
            TextButton(onClick = { shell.forget() }) { Text(stringResource(R.string.ssh_forget)) }
        }
    }
}

private const val SSH_SENTINEL = " "

@Composable
private fun SshKeyButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(37.5.dp)
            .background(if (active) Color(0xFF606060) else Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.SshTerminalPane(shell: SshShell) {
    val density = LocalDensity.current
    val charPx = 18.3f
    val linePx = 36f
    val fontSize = with(density) { (charPx / 0.6f).toSp() }
    val lineHeight = with(density) { linePx.toSp() }
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = !listState.canScrollForward
        }
    }
    val total = shell.lines.size + 1
    LaunchedEffect(total, shell.current) {
        if (following) listState.scrollToItem(total - 1)
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    var typed by remember { mutableStateOf(TextFieldValue(SSH_SENTINEL, TextRange(1))) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    fun typeText(s: String) {
        following = true
        if (s.isEmpty()) return
        val first = s[0]
        if (ctrl) {
            shell.send((first.uppercaseChar().code and 0x1f).toChar().toString())
            if (s.length > 1) shell.send(s.substring(1))
        } else if (alt) {
            shell.send("\u001B" + first)
            if (s.length > 1) shell.send(s.substring(1))
        } else {
            shell.send(s)
        }
        ctrl = false
        alt = false
    }
    fun press(label: String) {
        following = true
        when (label) {
            "ESC" -> shell.send("\u001B")
            "/" -> typeText("/")
            "—" -> typeText("-")
            "HOME" -> shell.send("\u001B[H")
            "END" -> shell.send("\u001B[F")
            "↑" -> shell.send("\u001B[A")
            "↓" -> shell.send("\u001B[B")
            "→" -> shell.send("\u001B[C")
            "←" -> shell.send("\u001B[D")
            "PGUP" -> shell.send("\u001B[5~")
            "PGDN" -> shell.send("\u001B[6~")
            "⇆" -> shell.send("\t")
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
        }
    }
    BoxWithConstraints(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                runCatching { focus.requestFocus() }
                keyboard?.show()
            },
    ) {
        val cols = (constraints.maxWidth / charPx).toInt().coerceAtLeast(20)
        val screenRows = (constraints.maxHeight / linePx).toInt().coerceAtLeast(5)
        LaunchedEffect(cols, screenRows) { shell.resize(cols, screenRows) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(total) { index ->
                val line = if (index < shell.lines.size) shell.lines[index] else shell.current
                if (line.length <= cols) {
                    SshRow(if (line.isEmpty()) AnnotatedString(" ") else line, fontSize, lineHeight)
                } else {
                    Column {
                        var s = 0
                        while (s < line.length) {
                            SshRow(line.subSequence(s, minOf(line.length, s + cols)), fontSize, lineHeight)
                            s += cols
                        }
                    }
                }
            }
        }
        BasicTextField(
            value = typed,
            onValueChange = { v ->
                val t = v.text
                if (t.isEmpty()) {
                    following = true
                    shell.send("\u007F")
                } else if (t != SSH_SENTINEL) {
                    val at = t.indexOf(' ')
                    typeText(if (at >= 0) t.removeRange(at, at + 1) else t)
                }
                typed = TextFieldValue(SSH_SENTINEL, TextRange(1))
            },
            modifier = Modifier.size(1.dp).focusRequester(focus),
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { shell.send("\r") }),
        )
    }
    val keyRows = listOf(
        listOf("ESC", "/", "—", "HOME", "↑", "END", "PGUP"),
        listOf("⇆", "CTRL", "ALT", "←", "↓", "→", "PGDN"),
    )
    for (keys in keyRows) {
        Row(Modifier.fillMaxWidth()) {
            for (label in keys) {
                SshKeyButton(
                    label = label,
                    active = (label == "CTRL" && ctrl) || (label == "ALT" && alt),
                    modifier = Modifier.weight(1f),
                    onClick = { press(label) },
                )
            }
        }
    }
}

@Composable
private fun SshRow(text: AnnotatedString, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) {
    Text(
        text,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        lineHeight = lineHeight,
        softWrap = false,
        maxLines = 1,
    )
}
