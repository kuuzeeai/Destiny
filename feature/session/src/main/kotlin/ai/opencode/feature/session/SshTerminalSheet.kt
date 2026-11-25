package ai.opencode.feature.session

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
    val lines = mutableStateListOf<String>()
    var current by mutableStateOf("")

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
        shell.setPtyType("dumb", 80, 24, 0, 0)
        val input = shell.inputStream
        output = shell.outputStream
        shell.connect(10000)
        channel = shell
        scope.launch {
            lines.clear()
            edit.setLength(0)
            cursor = 0
            current = ""
            if (note != null) lines.add(note)
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
        var text = (pendingEscape + chunk).replace(sshOsc, "").replace(sshCsi, "").replace(sshCharset, "")
        val tail = sshIncomplete.find(text)
        if (tail != null) {
            pendingEscape = tail.value.take(256)
            text = text.substring(0, tail.range.first)
        } else {
            pendingEscape = ""
        }
        text = text.replace("\u001B", "")
        for (c in text) {
            when (c) {
                '\n' -> {
                    lines.add(edit.toString())
                    edit.setLength(0)
                    cursor = 0
                }
                '\r' -> cursor = 0
                '\b' -> if (cursor > 0) cursor--
                '\t' -> repeat(4) { put(' ') }
                else -> if (c >= ' ') put(c)
            }
        }
        if (lines.size > SSH_MAX_LINES) lines.subList(0, 500).clear()
        current = edit.toString()
    }

    private fun put(c: Char) {
        if (cursor < edit.length) edit.setCharAt(cursor, c) else edit.append(c)
        cursor++
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
    val background = if (connected) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface
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
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 4.dp),
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

@Composable
private fun SshKeyButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .background(if (active) Color(0xFF30363D) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFC9D1D9), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnScope.SshTerminalPane(shell: SshShell) {
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
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        SelectionContainer {
            LazyColumn(state = listState) {
                items(total) { index ->
                    val text = if (index < shell.lines.size) shell.lines[index] else shell.current
                    Text(
                        text.ifEmpty { " " },
                        color = Color(0xFFC9D1D9),
                        fontFamily = OpenCodeFonts.monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
    var input by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    fun submit() {
        shell.send(input + "\n")
        input = ""
    }
    fun press(label: String) {
        when (label) {
            "ESC" -> shell.send("\u001B")
            "/" -> input += "/"
            "—" -> input += "-"
            "HOME" -> shell.send("\u001B[H")
            "END" -> shell.send("\u001B[F")
            "↑" -> shell.send("\u001B[A")
            "↓" -> shell.send("\u001B[B")
            "→" -> shell.send("\u001B[C")
            "←" -> shell.send("\u001B[D")
            "PGUP" -> shell.send("\u001B[5~")
            "PGDN" -> shell.send("\u001B[6~")
            "TAB" -> {
                shell.send(input + "\t")
                input = ""
            }
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
        }
    }
    val light = Color(0xFFC9D1D9)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { new ->
                if ((ctrl || alt) && new.length == input.length + 1 && new.startsWith(input)) {
                    val c = new.last()
                    if (ctrl) shell.send((c.uppercaseChar().code and 0x1f).toChar().toString())
                    else shell.send("\u001B" + c)
                    ctrl = false
                    alt = false
                } else {
                    input = new
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontFamily = OpenCodeFonts.monospace, fontSize = 14.sp, color = light),
            placeholder = { Text(stringResource(R.string.ssh_command_hint), color = Color(0xFF8B949E)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = light,
                unfocusedTextColor = light,
                focusedBorderColor = Color(0xFF8B949E),
                unfocusedBorderColor = Color(0xFF30363D),
                cursorColor = light,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        TextButton(
            onClick = { submit() },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_send)) }
    }
    val rows = listOf(
        listOf("ESC", "/", "—", "HOME", "↑", "END", "PGUP"),
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN"),
    )
    for (row in rows) {
        Row(Modifier.fillMaxWidth()) {
            for (label in row) {
                SshKeyButton(
                    label = label,
                    active = (label == "CTRL" && ctrl) || (label == "ALT" && alt),
                    modifier = Modifier.weight(1f),
                    onClick = { press(label) },
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { shell.send("\u0003") },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_ctrl_c)) }
        TextButton(
            onClick = { shell.clear() },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_clear)) }
        TextButton(
            onClick = { shell.leave() },
            colors = ButtonDefaults.textButtonColors(contentColor = light),
        ) { Text(stringResource(R.string.ssh_disconnect)) }
    }
}
