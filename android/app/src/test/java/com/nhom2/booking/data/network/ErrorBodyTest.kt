package com.nhom2.booking.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorBodyTest {

    @Test
    fun readsMessageAndCodeFromTheBackendErrorJson() {
        val body = ErrorBody.parse("""{"message":"Email này đã có tài khoản","code":"error.email.exists"}""")

        assertEquals(ErrorBody("Email này đã có tài khoản", "error.email.exists"), body)
    }

    @Test
    fun frameworkErrorsWithoutCodeKeepTheirMessage() {
        assertEquals(ErrorBody("Bad Request", null), ErrorBody.parse("""{"message":"Bad Request"}"""))
    }

    @Test
    fun plainTextBodiesBecomeTheMessage() {
        assertEquals(ErrorBody("Service down", null), ErrorBody.parse("  Service down \n"))
    }

    @Test
    fun brokenJsonIsTreatedAsPlainText() {
        assertEquals(ErrorBody("{not json", null), ErrorBody.parse("{not json"))
    }

    @Test
    fun emptyOrBlankFieldsAreIgnored() {
        assertEquals(ErrorBody(null, null), ErrorBody.parse(null))
        assertEquals(ErrorBody(null, null), ErrorBody.parse("   "))
        assertEquals(ErrorBody(null, "error.x"), ErrorBody.parse("""{"message":" ","code":"error.x"}"""))
    }

    @Test
    fun nonStringFieldsAreIgnored() {
        assertEquals(ErrorBody(null, null), ErrorBody.parse("""{"message":42,"code":{"a":1}}"""))
    }
}
