#!/usr/bin/env python3
# m3e_f25.py - Destiny
#  1) Burbuja "Mi terminal" arriba de "Archivadas" en el menu lateral; "Archivadas" tambien en burbuja.
#  2) Mi terminal (SSH): escribes comandos al celular viejo (JSch, llave guardada cifrada, huella verificada).
#  3) La terminal del chat (solo lectura) se pega mas al borde de abajo, separada.
# Se puede aplicar varias veces: cada cambio revisa si ya esta hecho.
# Uso: cd ~/Destiny && python m3e_f25.py
import os
import sys

ROOT = os.getcwd()
SESSION = "feature/session/src/main/kotlin/ai/opencode/feature/session/"
DRAWER = "feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/"
changed = []


def path(rel):
    return os.path.join(ROOT, rel)


def read(rel):
    with open(path(rel), encoding="utf-8") as f:
        return f.read()


def write(rel, text):
    with open(path(rel), "w", encoding="utf-8") as f:
        f.write(text)


def edit(rel, old, new, marker):
    if not os.path.exists(path(rel)):
        sys.exit("NO EXISTE: " + rel + "\n(¿estas en ~/Destiny?)")
    text = read(rel)
    if marker in text:
        print("  ya estaba:", rel, "->", marker[:48])
        return
    n = text.count(old)
    if n != 1:
        sys.exit("NO PUDE EDITAR " + rel + ": encontre " + str(n) + " veces:\n" + old[:160])
    write(rel, text.replace(old, new, 1))
    changed.append(rel)
    print("  editado:", rel, "->", marker[:48])


def add_strings(rel, marker, block):
    text = read(rel)
    if marker in text:
        print("  ya estaba:", rel, "->", marker)
        return
    end = text.rfind("</resources>")
    if end < 0:
        sys.exit("NO ENCUENTRO </resources> en " + rel)
    write(rel, text[:end] + block + text[end:])
    changed.append(rel)
    print("  editado:", rel, "->", marker)


if not os.path.isdir(path("feature/session")) or not os.path.isdir(path("feature/drawer")):
    sys.exit("Esto no es ~/Destiny (faltan feature/session o feature/drawer).")

print("1/7 Dependencia JSch")
edit("gradle/libs.versions.toml",
     'okhttp = "5.5.1"\n',
     'okhttp = "5.5.1"\njsch = "0.2.21"\n',
     'jsch = "0.2.21"')
edit("gradle/libs.versions.toml",
     'okhttp-sse = { module = "com.squareup.okhttp3:okhttp-sse", version.ref = "okhttp" }\n',
     'okhttp-sse = { module = "com.squareup.okhttp3:okhttp-sse", version.ref = "okhttp" }\n'
     '# :feature:session (Mi terminal SSH)\n'
     'jsch = { module = "com.github.mwiede:jsch", version.ref = "jsch" }\n',
     'com.github.mwiede:jsch')
edit("feature/session/build.gradle.kts",
     '    implementation(libs.coil.svg)\n',
     '    implementation(libs.coil.svg)\n'
     '    // Mi terminal (SSH) del menu lateral.\n'
     '    implementation(libs.jsch)\n',
     'libs.jsch')

print("2/7 Reglas R8")
rules = read("app/proguard-rules.pro")
if "com.jcraft.jsch" in rules:
    print("  ya estaba: app/proguard-rules.pro")
else:
    if not rules.endswith("\n"):
        rules += "\n"
    rules += (
        "\n# Mi terminal (SSH): JSch carga algoritmos por nombre de clase (reflexion); R8 no debe quitarlos.\n"
        "-keep class com.jcraft.jsch.** { *; }\n"
        "-dontwarn com.jcraft.jsch.**\n"
        "-dontwarn org.slf4j.**\n"
        "-dontwarn org.apache.logging.log4j.**\n"
        "-dontwarn com.sun.jna.**\n"
        "-dontwarn org.bouncycastle.**\n"
        "-dontwarn javax.naming.**\n"
        "-dontwarn javax.security.auth.kerberos.**\n"
        "-dontwarn java.lang.management.**\n"
        "-dontwarn org.ietf.jgss.**\n"
        "-dontwarn sun.misc.**\n"
    )
    write("app/proguard-rules.pro", rules)
    changed.append("app/proguard-rules.pro")
    print("  editado: app/proguard-rules.pro")

print("3/7 Textos")
ssh_en = '''    <!-- Mi terminal (SSH) -->
    <string name="ssh_title">My terminal (SSH)</string>
    <string name="ssh_host">Address (IP)</string>
    <string name="ssh_port">Port</string>
    <string name="ssh_user">User</string>
    <string name="ssh_password">Password (first time only)</string>
    <string name="ssh_key_hint">A key is already saved: you can leave the password empty.</string>
    <string name="ssh_connect">Connect</string>
    <string name="ssh_connecting">Connecting…</string>
    <string name="ssh_forget">Forget key and fingerprint</string>
    <string name="ssh_command_hint">Type a command</string>
    <string name="ssh_send">Send</string>
    <string name="ssh_ctrl_c">Ctrl+C</string>
    <string name="ssh_clear">Clear</string>
    <string name="ssh_disconnect">Disconnect</string>
    <string name="ssh_trust_title">First connection to this server: check the fingerprint</string>
    <string name="ssh_trust_yes">Trust and continue</string>
    <string name="ssh_trust_no">Cancel</string>
    <string name="ssh_err_auth">Could not sign in: wrong user or password, or the saved key no longer works. Type the password.</string>
    <string name="ssh_err_changed">The server fingerprint changed, so it did not connect. If you reinstalled Termux on the old phone, tap Forget key and fingerprint.</string>
    <string name="ssh_err_rejected">Cancelled: you did not trust the fingerprint.</string>
    <string name="ssh_err_network">Could not connect. Check that the old phone is on, on the same Wi-Fi and with sshd running.</string>
    <string name="ssh_err_no_key">The password is missing: there is no saved key yet.</string>
    <string name="ssh_err_closed">The session was closed.</string>
    <string name="ssh_err_generic">Error: %1$s</string>
    <string name="ssh_key_saved">[Key saved: next time it will sign in without a password]</string>
    <string name="ssh_key_failed">[Could not save the key; next time it will ask for the password]</string>
'''
ssh_es = '''    <!-- Mi terminal (SSH) -->
    <string name="ssh_title">Mi terminal (SSH)</string>
    <string name="ssh_host">Dirección (IP)</string>
    <string name="ssh_port">Puerto</string>
    <string name="ssh_user">Usuario</string>
    <string name="ssh_password">Contraseña (solo la primera vez)</string>
    <string name="ssh_key_hint">Ya hay una llave guardada: puedes dejar la contraseña vacía.</string>
    <string name="ssh_connect">Conectar</string>
    <string name="ssh_connecting">Conectando…</string>
    <string name="ssh_forget">Olvidar llave y huella</string>
    <string name="ssh_command_hint">Escribe un comando</string>
    <string name="ssh_send">Enviar</string>
    <string name="ssh_ctrl_c">Ctrl+C</string>
    <string name="ssh_clear">Limpiar</string>
    <string name="ssh_disconnect">Desconectar</string>
    <string name="ssh_trust_title">Primera conexión a este servidor: verifica la huella</string>
    <string name="ssh_trust_yes">Confiar y continuar</string>
    <string name="ssh_trust_no">Cancelar</string>
    <string name="ssh_err_auth">No se pudo entrar: usuario o contraseña incorrectos, o la llave guardada ya no sirve. Escribe la contraseña.</string>
    <string name="ssh_err_changed">La huella del servidor cambió, por eso no se conectó. Si reinstalaste Termux en el celular viejo, toca Olvidar llave y huella.</string>
    <string name="ssh_err_rejected">Cancelado: no confiaste en la huella.</string>
    <string name="ssh_err_network">No se pudo conectar. Revisa que el celular viejo esté encendido, en la misma Wi-Fi y con sshd activo.</string>
    <string name="ssh_err_no_key">Falta la contraseña: todavía no hay una llave guardada.</string>
    <string name="ssh_err_closed">La sesión se cerró.</string>
    <string name="ssh_err_generic">Error: %1$s</string>
    <string name="ssh_key_saved">[Llave guardada: la próxima vez entrará sin contraseña]</string>
    <string name="ssh_key_failed">[No se pudo guardar la llave; la próxima vez pedirá la contraseña]</string>
'''
add_strings("feature/session/src/main/res/values/strings.xml", 'name="ssh_title"', ssh_en)
add_strings("feature/session/src/main/res/values-es/strings.xml", 'name="ssh_title"', ssh_es)
add_strings("feature/drawer/src/main/res/values/strings.xml", 'name="drawer_terminal_entry"',
            '    <string name="drawer_terminal_entry">My terminal</string>\n')
add_strings("feature/drawer/src/main/res/values-es/strings.xml", 'name="drawer_terminal_entry"',
            '    <string name="drawer_terminal_entry">Mi terminal</string>\n')

print("4/7 Mi terminal (SSH): pantalla nueva")
KOTLIN = r'''package ai.opencode.feature.session

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalSheet(onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val shell = remember { SshShell(context, SshStore(context), scope) }
    DisposableEffect(shell) { onDispose { shell.disconnect() } }
    LaunchedEffect(shell) { shell.autoConnect() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    OpenCodeBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .padding(horizontal = 4.dp)
                .padding(bottom = 4.dp),
        ) {
            Text(
                stringResource(R.string.ssh_title),
                style = OpenCodeTextStyles.callout.copy(fontWeight = FontWeight.SemiBold),
                color = OpenCodeTheme.colors.text,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
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
            .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
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
    fun submit() {
        shell.send(input + "\n")
        input = ""
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontFamily = OpenCodeFonts.monospace, fontSize = 14.sp),
            placeholder = { Text(stringResource(R.string.ssh_command_hint)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        TextButton(onClick = { submit() }) { Text(stringResource(R.string.ssh_send)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { shell.send("\u0003") }) { Text(stringResource(R.string.ssh_ctrl_c)) }
        TextButton(onClick = { shell.clear() }) { Text(stringResource(R.string.ssh_clear)) }
        TextButton(onClick = { shell.leave() }) { Text(stringResource(R.string.ssh_disconnect)) }
    }
}
'''
write(SESSION + "SshTerminalSheet.kt", KOTLIN)
print("  escrito:", SESSION + "SshTerminalSheet.kt")

print("5/7 Terminal del chat: mas abajo, separada")
TL = SESSION + "TerminalLogView.kt"
edit(TL,
     "import androidx.compose.foundation.layout.padding\n",
     "import androidx.compose.foundation.layout.padding\n"
     "import androidx.compose.foundation.layout.WindowInsets\n"
     "import androidx.compose.foundation.layout.navigationBars\n"
     "import androidx.compose.ui.layout.layout\n"
     "import androidx.compose.ui.platform.LocalDensity\n",
     "import androidx.compose.foundation.layout.navigationBars")
edit(TL,
     "    OpenCodeBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {\n",
     "    // La caja negra baja hasta casi el borde de la pantalla (sobre la barra de navegacion), separada por 4 dp.\n"
     "    val density = LocalDensity.current\n"
     "    val extraBottomPx = WindowInsets.navigationBars.getBottom(density)\n"
     "    OpenCodeBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {\n",
     "val extraBottomPx")
edit(TL,
     "                .padding(horizontal = 4.dp)\n                .padding(bottom = 8.dp),\n",
     "                .padding(horizontal = 4.dp)\n                .padding(bottom = 4.dp),\n",
     ".padding(bottom = 4.dp),")
edit(TL,
     "                    .weight(1f)\n"
     "                    .fillMaxWidth()\n"
     "                    .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))\n"
     "                    .padding(horizontal = 8.dp, vertical = 10.dp),\n",
     "                    .weight(1f)\n"
     "                    .fillMaxWidth()\n"
     "                    .layout { measurable, constraints ->\n"
     "                        val extra = if (constraints.hasBoundedHeight) extraBottomPx else 0\n"
     "                        val placeable = measurable.measure(\n"
     "                            constraints.copy(\n"
     "                                minHeight = constraints.minHeight + extra,\n"
     "                                maxHeight = constraints.maxHeight + extra,\n"
     "                            ),\n"
     "                        )\n"
     "                        layout(placeable.width, placeable.height - extra) { placeable.place(0, 0) }\n"
     "                    }\n"
     "                    .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))\n"
     "                    .padding(\n"
     "                        start = 8.dp,\n"
     "                        end = 8.dp,\n"
     "                        top = 10.dp,\n"
     "                        bottom = 10.dp + with(density) { extraBottomPx.toDp() },\n"
     "                    ),\n",
     "val extra = if (constraints.hasBoundedHeight)")

print("6/7 Menu lateral: burbujas")
MODEL = DRAWER + "list/DrawerListModel.kt"
edit(MODEL,
     "    data object Archived : DrawerListItem\n",
     "    data object Archived : DrawerListItem\n"
     "    /** Burbuja encima de Archivadas: abre Mi terminal (SSH). */\n"
     "    data object Terminal : DrawerListItem\n",
     "data object Terminal : DrawerListItem")
edit(MODEL,
     '            Archived -> "archived"\n',
     '            Archived -> "archived"\n            Terminal -> "terminal"\n',
     'Terminal -> "terminal"')
edit(MODEL,
     "                if (archivedCount > 0) add(DrawerListItem.Archived)\n",
     "                if (plain) add(DrawerListItem.Terminal)\n"
     "                if (archivedCount > 0) add(DrawerListItem.Archived)\n",
     "add(DrawerListItem.Terminal)")
edit(MODEL,
     "val showsShortcuts = plain && (shortcuts.showsCatchUp || shortcuts.showsWhatsNew) || archivedCount > 0",
     "val showsShortcuts = plain || archivedCount > 0",
     "val showsShortcuts = plain || archivedCount > 0")

VIEW = DRAWER + "list/DrawerListView.kt"
edit(VIEW,
     "    val openArchived: () -> Unit,\n)",
     "    val openArchived: () -> Unit,\n    val openTerminal: () -> Unit = {},\n)",
     "val openTerminal")
edit(VIEW,
     "                    DrawerListItem.Archived -> ArchivedEntryRow(",
     "                    DrawerListItem.Terminal -> TerminalEntryRow(onClick = { currentActions.openTerminal() })\n"
     "                    DrawerListItem.Archived -> ArchivedEntryRow(",
     "DrawerListItem.Terminal -> TerminalEntryRow")
edit(VIEW,
     "    DrawerListItem.CatchUp, DrawerListItem.WhatsNew, DrawerListItem.Archived -> 0",
     "    DrawerListItem.CatchUp, DrawerListItem.WhatsNew, DrawerListItem.Archived, DrawerListItem.Terminal -> 0",
     "DrawerListItem.Archived, DrawerListItem.Terminal -> 0")

ARCH = DRAWER + "list/ArchivedPage.kt"
edit(ARCH,
     "import androidx.compose.foundation.shape.RoundedCornerShape\n",
     "import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\n",
     "import androidx.compose.foundation.shape.CircleShape")
edit(ARCH,
     ".clip(RoundedCornerShape(if (regularList) DrawerMetrics.rowCornerRadius else 0.dp))",
     ".clip(if (regularList) CircleShape else RoundedCornerShape(0.dp))",
     ".clip(if (regularList) CircleShape")
edit(ARCH,
     ".padding(start = start, end = end),",
     ".padding(start = if (regularList) start + 2.dp else start, end = if (regularList) end + 2.dp else end),",
     "if (regularList) start + 2.dp")
edit(ARCH,
     "/**\n * The Archived page over the drawer's list: back,",
     "/** Burbuja encima de Archivadas: abre Mi terminal (SSH). */\n"
     "@Composable\n"
     "internal fun TerminalEntryRow(onClick: () -> Unit) {\n"
     "    val colors = OpenCodeTheme.colors\n"
     "    Row(\n"
     "        Modifier\n"
     "            .fillMaxWidth()\n"
     "            .padding(horizontal = DrawerMetrics.rowEdge, vertical = 3.dp)\n"
     "            .clip(CircleShape)\n"
     "            .background(MaterialTheme.colorScheme.secondaryContainer)\n"
     "            .clickable(role = Role.Button, onClick = onClick)\n"
     "            .heightIn(min = DrawerMetrics.rowHeight)\n"
     "            .padding(start = DrawerMetrics.textInset + 2.dp, end = DrawerMetrics.trailingInset + 2.dp),\n"
     "        verticalAlignment = Alignment.CenterVertically,\n"
     "        horizontalArrangement = Arrangement.spacedBy(8.dp),\n"
     "    ) {\n"
     "        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {\n"
     "            OpenCodeIcon(SFSymbol.Terminal, contentDescription = null, tint = colors.secondaryText)\n"
     "        }\n"
     "        Text(\n"
     "            stringResource(R.string.drawer_terminal_entry),\n"
     "            style = DrawerTunables.rowFont.font,\n"
     "            color = colors.text,\n"
     "            modifier = Modifier.weight(1f),\n"
     "        )\n"
     "    }\n"
     "}\n\n"
     "/**\n * The Archived page over the drawer's list: back,",
     "internal fun TerminalEntryRow")

HOME = DRAWER + "HomeViewDrawer.kt"
edit(HOME,
     "import ai.opencode.feature.session.ComposerTunables\n",
     "import ai.opencode.feature.session.ComposerTunables\nimport ai.opencode.feature.session.SshTerminalSheet\n",
     "import ai.opencode.feature.session.SshTerminalSheet")
edit(HOME,
     "    val actions = remember(store, viewState) {\n",
     "    var showsTerminal by remember { mutableStateOf(false) }\n    val actions = remember(store, viewState) {\n",
     "var showsTerminal")
edit(HOME,
     "            hideWhatsNew = { store.send(HomeFeature.Action.WhatsNew(WhatsNewFeature.Action.HideTapped)) },\n"
     "            openArchived = openArchived,\n",
     "            hideWhatsNew = { store.send(HomeFeature.Action.WhatsNew(WhatsNewFeature.Action.HideTapped)) },\n"
     "            openArchived = openArchived,\n            openTerminal = { showsTerminal = true },\n",
     "openTerminal = { showsTerminal = true }")
edit(HOME,
     "        OpenCodeNoticePresence(\n            failure,\n",
     "        if (showsTerminal) SshTerminalSheet(onClose = { showsTerminal = false })\n\n"
     "        OpenCodeNoticePresence(\n            failure,\n",
     "SshTerminalSheet(onClose")

print("7/7 Revision")
for root, _, files in os.walk(path("feature")):
    for name in files:
        if name.endswith(".kt") and name != "SshTerminalSheet.kt":
            with open(os.path.join(root, name), encoding="utf-8") as f:
                if "com.jcraft" in f.read():
                    print("  AVISO: otro archivo usa JSch:", os.path.join(root, name))

print()
if changed:
    print("LISTO. Archivos tocados:", len(set(changed)) + 1)
else:
    print("LISTO. Ya estaba todo aplicado.")
