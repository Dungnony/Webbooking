package com.nhom2.booking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.nhom2.booking.data.api.AccountApi
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.api.BookingApi
import com.nhom2.booking.data.api.CatalogApi
import com.nhom2.booking.data.auth.AuthApi
import com.nhom2.booking.data.auth.AuthRepository
import com.nhom2.booking.data.catalog.HotelRepository
import com.nhom2.booking.data.chat.SupportChatSocket
import com.nhom2.booking.data.network.ApiFactory
import com.nhom2.booking.data.session.SessionStore
import com.nhom2.booking.data.session.sessionDataStore
import com.nhom2.booking.data.wishlist.WishlistRepository
import com.nhom2.booking.domain.GuestDetails
import com.nhom2.booking.domain.StayQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

private val Context.wishlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "wishlist")

/** App-wide singletons, created once in [BookingApplication]. */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(context.sessionDataStore, appScope)

    private val httpClient = ApiFactory.createClient(
        tokenProvider = sessionStore::currentToken,
        onUnauthorized = { appScope.launch { sessionStore.clear() } },
        enableLogging = BuildConfig.DEBUG,
    )
    private val retrofit = ApiFactory.createRetrofit(BuildConfig.API_BASE_URL, httpClient)

    val catalogApi: CatalogApi = retrofit.create(CatalogApi::class.java)
    val bookingApi: BookingApi = retrofit.create(BookingApi::class.java)
    val accountApi: AccountApi = retrofit.create(AccountApi::class.java)
    val adminApi: AdminApi = retrofit.create(AdminApi::class.java)

    val authRepository = AuthRepository(retrofit.create(AuthApi::class.java), sessionStore)

    val hotelRepository = HotelRepository(catalogApi)

    /** Last search, shared by the home, results and booking screens. */
    val stayQuery = MutableStateFlow(StayQuery.default(LocalDate.now()))

    val wishlistRepository = WishlistRepository(accountApi, context.wishlistDataStore, sessionStore, appScope)

    val supportChatSocket = SupportChatSocket(
        client = httpClient,
        baseUrl = BuildConfig.API_BASE_URL,
        tokenProvider = sessionStore::currentToken,
        json = ApiFactory.json,
    )

    /**
     * Name and phone typed on the booking form, keyed by booking id. The backend does not store them, so they
     * only live for this app session to be shown on the payment and confirmation screens (as on the web).
     */
    val guestDetailsByBooking: MutableMap<Long, GuestDetails> = ConcurrentHashMap()
}
