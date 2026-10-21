package com.nhom2.booking.data.chat

private const val FRAME_END = '\u0000'

/** The subset of STOMP 1.2 the support chat needs: CONNECT, SUBSCRIBE out; CONNECTED, MESSAGE, ERROR in. */
data class StompFrame(val command: String, val headers: Map<String, String>, val body: String) {

    fun encode(): String = buildString {
        // STOMP 1.2 leaves CONNECT headers unescaped, e.g. a "host:port" value keeps its colon.
        val escapeHeaders = command != CONNECT
        append(command).append('\n')
        headers.forEach { (name, value) ->
            append(name).append(':').append(if (escapeHeaders) escape(value) else value).append('\n')
        }
        append('\n').append(body).append(FRAME_END)
    }

    companion object {
        private const val CONNECT = "CONNECT"
        private const val CONNECTED = "CONNECTED"

        fun connect(host: String, bearerToken: String) = StompFrame(
            CONNECT,
            linkedMapOf(
                "accept-version" to "1.2",
                "host" to host,
                // Server heart-beats are not needed: OkHttp pings the socket itself.
                "heart-beat" to "0,0",
                "Authorization" to "Bearer $bearerToken",
            ),
            "",
        )

        fun subscribe(id: String, destination: String) =
            StompFrame("SUBSCRIBE", linkedMapOf("id" to id, "destination" to destination), "")

        /** One WebSocket message may hold several frames, plus bare newlines sent as heart-beats. */
        fun parseAll(text: String): List<StompFrame> =
            text.split(FRAME_END).mapNotNull { parse(it) }

        private fun parse(raw: String): StompFrame? {
            val frame = raw.trimStart('\n', '\r')
            if (frame.isEmpty()) return null
            val headerEnd = frame.indexOf("\n\n").takeIf { it >= 0 }
            val head = if (headerEnd == null) frame else frame.substring(0, headerEnd)
            val body = if (headerEnd == null) "" else frame.substring(headerEnd + 2)
            val lines = head.lines().map { it.trimEnd('\r') }
            val command = lines.first()
            val headers = lines.drop(1)
                .filter { it.contains(':') }
                .associate { line ->
                    val value = line.substringAfter(':')
                    line.substringBefore(':') to if (command == CONNECTED) value else unescape(value)
                }
            return StompFrame(command, headers, body)
        }

        private fun escape(value: String) = buildString {
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    ':' -> append("\\c")
                    else -> append(c)
                }
            }
        }

        private fun unescape(value: String) = buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        'n' -> append('\n')
                        'r' -> append('\r')
                        'c' -> append(':')
                        '\\' -> append('\\')
                        else -> append(c).append(value[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
