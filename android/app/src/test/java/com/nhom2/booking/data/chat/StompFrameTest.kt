package com.nhom2.booking.data.chat

import com.nhom2.booking.data.model.SupportMessage
import com.nhom2.booking.ui.chat.appendUnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StompFrameTest {

    @Test
    fun connectFrameCarriesTheBearerTokenAndKeepsColonsUnescaped() {
        val encoded = StompFrame.connect("10.0.2.2:8080", "abc.def.ghi").encode()

        assertTrue(encoded.startsWith("CONNECT\n"))
        assertTrue(encoded.contains("host:10.0.2.2:8080\n"))
        assertTrue(encoded.contains("Authorization:Bearer abc.def.ghi\n"))
        assertTrue(encoded.endsWith("\n\n\u0000"))
    }

    @Test
    fun subscribeFrameNamesTheDestination() {
        assertEquals(
            "SUBSCRIBE\nid:sub-0\ndestination:/user/queue/support-chat\n\n\u0000",
            StompFrame.subscribe("sub-0", "/user/queue/support-chat").encode(),
        )
    }

    @Test
    fun parsesSeveralFramesAndSkipsHeartBeats() {
        val text = "\nCONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000" +
            "MESSAGE\ndestination:/user/queue/support-chat\ncontent-type:application/json\n\n{\"id\":1}\u0000\n"

        val frames = StompFrame.parseAll(text)

        assertEquals(listOf("CONNECTED", "MESSAGE"), frames.map { it.command })
        assertEquals("1.2", frames[0].headers["version"])
        assertEquals("{\"id\":1}", frames[1].body)
        assertEquals("/user/queue/support-chat", frames[1].headers["destination"])
    }

    @Test
    fun unescapesHeaderValuesExceptOnConnected() {
        val frames = StompFrame.parseAll("ERROR\nmessage:Bad\\cthing\\nhere\n\n\u0000")

        assertEquals("Bad:thing\nhere", frames.single().headers["message"])
    }

    @Test
    fun appendUniqueDropsTheEchoOfAMessageAlreadyShown() {
        val first = SupportMessage(id = 1, content = "hi")
        val list = listOf(first)

        assertEquals(list, list.appendUnique(first.copy()))
        assertEquals(2, list.appendUnique(SupportMessage(id = 2, content = "yo")).size)
    }
}
