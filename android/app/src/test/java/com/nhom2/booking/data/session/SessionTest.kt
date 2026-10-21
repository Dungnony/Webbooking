package com.nhom2.booking.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SessionTest {

    private fun tokenWithPayload(json: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
        val payload = encoder.encodeToString(json.toByteArray())
        return "$header.$payload.signature"
    }

    @Test
    fun readsUsernameRoleAndExpiryFromTheBackendToken() {
        val token = tokenWithPayload("""{"sub":"alice","role":"ADMIN","iat":1700000000,"exp":1700086400}""")

        val session = Session.fromToken(token)

        assertNotNull(session)
        assertEquals("alice", session!!.username)
        assertEquals("ADMIN", session.role)
        assertTrue(session.isAdmin)
        assertEquals(1700086400L, session.expiresAt)
        assertEquals(token, session.token)
    }

    @Test
    fun missingRoleDefaultsToUser() {
        val session = Session.fromToken(tokenWithPayload("""{"sub":"bob"}"""))

        assertEquals("USER", session?.role)
        assertFalse(session!!.isAdmin)
    }

    @Test
    fun decodesNonAsciiUsernames() {
        val session = Session.fromToken(tokenWithPayload("""{"sub":"Dũng"}"""))

        assertEquals("Dũng", session?.username)
    }

    @Test
    fun rejectsMalformedTokens() {
        assertNull(Session.fromToken("not-a-jwt"))
        assertNull(Session.fromToken("a.%%%.c"))
        assertNull(Session.fromToken(tokenWithPayload("not json")))
        assertNull(Session.fromToken(tokenWithPayload("""{"role":"USER"}""")))
    }

    @Test
    fun expiryIsComparedInMilliseconds() {
        val session = Session.fromToken(tokenWithPayload("""{"sub":"alice","exp":1000}"""))!!

        assertFalse(session.isExpired(nowMillis = 999_999))
        assertTrue(session.isExpired(nowMillis = 1_000_000))
    }

    @Test
    fun tokenWithoutExpiryNeverExpires() {
        val session = Session.fromToken(tokenWithPayload("""{"sub":"alice"}"""))!!

        assertFalse(session.isExpired(nowMillis = Long.MAX_VALUE))
    }
}
