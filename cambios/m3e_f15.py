import sys, pathlib
S = pathlib.Path("feature/system")
GR = S/"build.gradle.kts"
MF = S/"src/main/AndroidManifest.xml"
KT = S/"src/main/kotlin/ai/opencode/feature/system/notifications/OpenCodeMessagingService.kt"
TOML = pathlib.Path("gradle/libs.versions.toml")
EDITS = {
 GR: [("    implementation(libs.firebase.messaging)\n", "")],
 TOML: [
  ('firebaseMessaging = "26.0.1"\n', ""),
  ('firebase-messaging = { module = "com.google.firebase:firebase-messaging", version.ref = "firebaseMessaging" }\n', ""),
 ],
 MF: [
  ('''        <!-- Push (FCM), inert until a Firebase configuration ships: no token, no messages. -->
        <service
            android:name=".notifications.OpenCodeMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />
        <meta-data android:name="firebase_analytics_collection_enabled" android:value="false" />

''', ""),
 ],
}
NEW_KT = '''package ai.opencode.feature.system.notifications

import android.content.Context

/**
 * Push stand-in: Firebase was removed, so no push token is ever minted. Keeps the
 * [PushTokenProvider] seam that [SessionNotifications] uses; it fails at once.
 */
class FcmPushTokenProvider(@Suppress("UNUSED_PARAMETER") private val context: Context) : PushTokenProvider {
    override fun register(onToken: (String) -> Unit, onFailure: () -> Unit) = onFailure()

    companion object {
        /** Always false: the build carries no push configuration. */
        @Suppress("UNUSED_PARAMETER")
        fun isConfigured(context: Context): Boolean = false
    }
}
'''
out = {}
for path, edits in EDITS.items():
    if not path.exists(): sys.exit(f"NO EXISTE {path}")
    t = path.read_text()
    for old, new in edits:
        n = t.count(old)
        if n != 1: sys.exit(f"ABORTO: {path.name}: {n} coincidencias para: {old[:70]!r}")
        t = t.replace(old, new)
    if path.suffix == ".kts" and (t.count("{") != t.count("}") or t.count("(") != t.count(")")):
        sys.exit(f"ABORTO: balance roto en {path.name}")
    out[path] = t
if not KT.exists(): sys.exit(f"NO EXISTE {KT}")
out[KT] = NEW_KT
for p, t in out.items(): p.write_text(t)
print("OK f15:", ", ".join(p.name for p in out))
