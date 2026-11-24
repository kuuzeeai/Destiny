package ai.opencode.server.live

import ai.opencode.server.OpenCodeServer
import ai.opencode.server.model.OpenCodeTranscriptItem

/** Recent transcript of a session, for the voice assistant (no UI). */
suspend fun fetchRecentTranscript(server: OpenCodeServer, sessionID: String, limit: Int = 12): List<OpenCodeTranscriptItem> =
    requestTranscript(server, sessionID, limit)
