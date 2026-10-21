package com.nhom2.booking.data.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

sealed class ApiException(message: String?, cause: Throwable?) : Exception(message, cause) {

    /** The backend answered with an error status; [code] is its stable error key, e.g. `error.email.exists`. */
    class Http(val status: Int, val code: String?, val serverMessage: String?, cause: Throwable?) :
        ApiException("HTTP $status ${code.orEmpty()}", cause)

    /** The backend could not be reached (no network, DNS, timeout). */
    class Network(cause: IOException) : ApiException(cause.message, cause)

    /** The backend answered 2xx with something this app cannot read. */
    class UnexpectedResponse(message: String, cause: Throwable? = null) : ApiException(message, cause)
}

data class ErrorBody(val message: String?, val code: String?) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The backend answers errors with `{ message, code }`, but some framework errors are plain text. */
        fun parse(raw: String?): ErrorBody {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return ErrorBody(null, null)
            if (text.startsWith("{")) {
                val obj = try {
                    json.parseToJsonElement(text).jsonObject
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (obj != null) return ErrorBody(obj.stringField("message"), obj.stringField("code"))
            }
            return ErrorBody(text, null)
        }

        private fun JsonObject.stringField(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }
}

/** Runs a Retrofit call and turns transport and HTTP failures into [ApiException]. */
suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        // Retrofit buffers error bodies, so reading it here does not touch the network.
        val body = ErrorBody.parse(e.response()?.errorBody()?.string())
        throw ApiException.Http(e.code(), body.code, body.message, e)
    } catch (e: IOException) {
        throw ApiException.Network(e)
    } catch (e: SerializationException) {
        throw ApiException.UnexpectedResponse("Unreadable response", e)
    }
