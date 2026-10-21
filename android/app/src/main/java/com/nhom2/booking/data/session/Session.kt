package com.nhom2.booking.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

const val ROLE_ADMIN = "ADMIN"
private const val DEFAULT_ROLE = "USER"

data class Session(
    val token: String,
    val username: String,
    val role: String,
    /** Seconds since the epoch, or null when the token carries no expiry. */
    val expiresAt: Long?,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    fun isExpired(nowMillis: Long): Boolean = expiresAt != null && expiresAt * 1000 <= nowMillis

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reads the claims the backend puts in its JWT (sub, role, exp). The signature is not checked here;
         * the backend verifies the token on every request.
         */
        fun fromToken(token: String): Session? {
            val parts = token.split('.')
            if (parts.size != 3) return null
            val payload = try {
                val decoded = Base64.getUrlDecoder().decode(parts[1]).decodeToString()
                json.decodeFromString<JwtPayload>(decoded)
            } catch (e: IllegalArgumentException) {
                // Covers both bad base64 and bad JSON (SerializationException is an IllegalArgumentException).
                return null
            }
            val username = payload.sub?.takeIf { it.isNotBlank() } ?: return null
            return Session(token, username, payload.role ?: DEFAULT_ROLE, payload.exp)
        }
    }
}

@Serializable
private data class JwtPayload(
    @SerialName("sub") val sub: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("exp") val exp: Long? = null,
)
