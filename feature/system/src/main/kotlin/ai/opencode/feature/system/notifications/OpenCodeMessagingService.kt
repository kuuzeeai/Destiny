package ai.opencode.feature.system.notifications

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
