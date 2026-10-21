package com.nhom2.booking.data.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

// Railway may cold-start and OTP endpoints wait for SMTP, so the defaults (10 s) are too short.
private const val TIMEOUT_SECONDS = 30L
// Keeps the support-chat WebSocket alive through proxies that drop idle connections.
private const val WEBSOCKET_PING_SECONDS = 20L

object ApiFactory {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun createClient(
        tokenProvider: () -> String?,
        onUnauthorized: () -> Unit,
        enableLogging: Boolean,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .pingInterval(WEBSOCKET_PING_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(sessionInterceptor(tokenProvider, onUnauthorized))
        .apply {
            if (enableLogging) {
                // BASIC logs only method, URL and status: never tokens, passwords or OTPs.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        // Scalars first: several endpoints answer with a bare string (JWT, masked email, "Deleted"), not JSON.
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    private fun sessionInterceptor(tokenProvider: () -> String?, onUnauthorized: () -> Unit) =
        Interceptor { chain ->
            val token = tokenProvider()
            val request = chain.request().newBuilder()
                // The backend translates error messages, notifications and AI replies according to this header.
                .header("Accept-Language", Locale.getDefault().language)
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            val response = chain.proceed(request)
            // A 401 on a request that carried a token means the token expired or was revoked.
            if (response.code == 401 && token != null) onUnauthorized()
            response
        }
}
