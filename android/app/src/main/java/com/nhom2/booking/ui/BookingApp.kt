package com.nhom2.booking.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nhom2.booking.AppContainer
import com.nhom2.booking.R
import com.nhom2.booking.data.session.SessionState
import com.nhom2.booking.ui.account.AccountActions
import com.nhom2.booking.ui.account.AccountScreen
import com.nhom2.booking.ui.account.AccountViewModel
import com.nhom2.booking.ui.account.NotificationsScreen
import com.nhom2.booking.ui.account.NotificationsViewModel
import com.nhom2.booking.ui.account.ProfileSettingsScreen
import com.nhom2.booking.ui.account.ProfileSettingsViewModel
import com.nhom2.booking.ui.admin.AdminBookingsScreen
import com.nhom2.booking.ui.admin.AdminBookingsViewModel
import com.nhom2.booking.ui.admin.AdminHotelsScreen
import com.nhom2.booking.ui.admin.AdminHotelsViewModel
import com.nhom2.booking.ui.admin.AdminOverviewScreen
import com.nhom2.booking.ui.admin.AdminOverviewViewModel
import com.nhom2.booking.ui.admin.AdminRequestsScreen
import com.nhom2.booking.ui.admin.AdminRequestsViewModel
import com.nhom2.booking.ui.admin.AdminReviewsScreen
import com.nhom2.booking.ui.admin.AdminReviewsViewModel
import com.nhom2.booking.ui.admin.AdminRoomsScreen
import com.nhom2.booking.ui.admin.AdminRoomsViewModel
import com.nhom2.booking.ui.admin.AdminUsersScreen
import com.nhom2.booking.ui.admin.AdminUsersViewModel
import com.nhom2.booking.ui.auth.ForgotPasswordScreen
import com.nhom2.booking.ui.auth.ForgotPasswordViewModel
import com.nhom2.booking.ui.auth.LoginScreen
import com.nhom2.booking.ui.auth.LoginViewModel
import com.nhom2.booking.ui.auth.RegisterScreen
import com.nhom2.booking.ui.auth.RegisterViewModel
import com.nhom2.booking.ui.booking.BookingFormScreen
import com.nhom2.booking.ui.booking.BookingFormViewModel
import com.nhom2.booking.ui.booking.ConfirmationScreen
import com.nhom2.booking.ui.booking.ConfirmationViewModel
import com.nhom2.booking.ui.booking.PaymentScreen
import com.nhom2.booking.ui.booking.PaymentViewModel
import com.nhom2.booking.ui.bookings.MyBookingsScreen
import com.nhom2.booking.ui.bookings.MyBookingsViewModel
import com.nhom2.booking.ui.chat.AdminConversationViewModel
import com.nhom2.booking.ui.chat.AdminInboxScreen
import com.nhom2.booking.ui.chat.AdminInboxViewModel
import com.nhom2.booking.ui.chat.AssistantScreen
import com.nhom2.booking.ui.chat.AssistantViewModel
import com.nhom2.booking.ui.chat.ConversationScreen
import com.nhom2.booking.ui.chat.GuestSupportChatViewModel
import com.nhom2.booking.ui.chat.WhileVisible
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.hotel.HotelDetailScreen
import com.nhom2.booking.ui.hotel.HotelDetailViewModel
import com.nhom2.booking.ui.home.HomeScreen
import com.nhom2.booking.ui.home.HomeViewModel
import com.nhom2.booking.ui.search.SearchScreen
import com.nhom2.booking.ui.search.SearchViewModel
import com.nhom2.booking.ui.wishlist.WishlistScreen
import com.nhom2.booking.ui.wishlist.WishlistViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val HOTEL = "hotel/{hotelId}"
    const val BOOK = "book/{hotelId}?roomId={roomId}"
    const val PAYMENT = "payment/{bookingId}"
    const val CONFIRMATION = "confirmation/{bookingId}"
    const val WISHLIST = "wishlist"
    const val BOOKINGS = "bookings"
    const val ACCOUNT = "account"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val SUPPORT_CHAT = "support-chat"
    const val ASSISTANT = "assistant?hotelId={hotelId}"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"
    const val ADMIN_OVERVIEW = "admin/overview"
    const val ADMIN_BOOKINGS = "admin/bookings"
    const val ADMIN_REQUESTS = "admin/requests"
    const val ADMIN_HOTELS = "admin/hotels"
    const val ADMIN_ROOMS = "admin/hotels/{hotelId}/rooms?name={name}"
    const val ADMIN_USERS = "admin/users"
    const val ADMIN_REVIEWS = "admin/reviews"
    const val ADMIN_INBOX = "admin/inbox"
    const val ADMIN_CHAT = "admin/chat/{roomId}?username={username}"

    fun hotel(id: Long) = "hotel/$id"
    fun book(hotelId: Long, roomId: Long?) = "book/$hotelId" + (roomId?.let { "?roomId=$it" } ?: "")
    fun payment(bookingId: Long) = "payment/$bookingId"
    fun confirmation(bookingId: Long) = "confirmation/$bookingId"
    fun assistant(hotelId: Long?) = if (hotelId == null) "assistant" else "assistant?hotelId=$hotelId"
    fun adminRooms(hotelId: Long, name: String) = "admin/hotels/$hotelId/rooms?name=${Uri.encode(name)}"
    fun adminChat(roomId: Long, username: String) = "admin/chat/$roomId?username=${Uri.encode(username)}"
}

// Optional Long arguments cannot be null in Navigation, so "absent" is this value.
private const val NO_ID = -1L

private data class Tab(val route: String, @param:StringRes val label: Int, val icon: ImageVector)

private val GUEST_TABS = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
    Tab(Routes.WISHLIST, R.string.tab_wishlist, Icons.Filled.Favorite),
    Tab(Routes.BOOKINGS, R.string.tab_bookings, Icons.Filled.CalendarMonth),
    Tab(Routes.ACCOUNT, R.string.tab_account, Icons.Filled.Person),
)

private val ADMIN_TABS = listOf(
    Tab(Routes.ADMIN_OVERVIEW, R.string.tab_admin_overview, Icons.Filled.BarChart),
    Tab(Routes.ADMIN_BOOKINGS, R.string.tab_admin_bookings, Icons.Filled.CalendarMonth),
    Tab(Routes.ADMIN_REQUESTS, R.string.tab_admin_requests, Icons.AutoMirrored.Filled.Assignment),
    Tab(Routes.ADMIN_HOTELS, R.string.tab_admin_hotels, Icons.Filled.Apartment),
    Tab(Routes.ACCOUNT, R.string.tab_account, Icons.Filled.Person),
)

@Composable
fun BookingApp(container: AppContainer) {
    val sessionState by container.sessionStore.state.collectAsStateWithLifecycle()
    if (sessionState == SessionState.Loading) {
        LoadingBox()
        return
    }
    val isAdmin = (sessionState as? SessionState.LoggedIn)?.session?.isAdmin == true
    val navController = rememberNavController()
    val badges = viewModel { BadgesViewModel(container.accountApi, container.adminApi, container.sessionStore) }
    val unreadNotifications by badges.unreadNotifications.collectAsStateWithLifecycle()
    val unreadChat by badges.unreadChat.collectAsStateWithLifecycle()
    WhileVisible { badges.pollWhileVisible() }

    // One snackbar host above every screen, so a message survives the navigation it triggers.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val showMessage: (UiText) -> Unit = { text ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text.resolve(context))
        }
    }

    val startRoute = remember { if (isAdmin) Routes.ADMIN_OVERVIEW else Routes.HOME }
    // Signing in as an admin, or out of an admin account, swaps the whole set of tabs.
    LaunchedEffect(isAdmin) {
        badges.refresh()
        val target = if (isAdmin) Routes.ADMIN_OVERVIEW else Routes.HOME
        val onAdminSide = navController.currentDestination?.route?.startsWith("admin/") == true
        if (navController.currentDestination != null && onAdminSide != isAdmin) {
            navController.navigate(target) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tabs = if (isAdmin) ADMIN_TABS else GUEST_TABS

    // The tab bar handles the system navigation bar itself; each screen's own Scaffold handles the rest.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val badge = if (tab.route == Routes.ACCOUNT) unreadNotifications + unreadChat else 0
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.openTab(tab.route) },
                            icon = {
                                BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            AppNavHost(
                navController = navController,
                startRoute = startRoute,
                container = container,
                sessionState = sessionState,
                unreadNotifications = unreadNotifications,
                unreadChat = unreadChat,
                onBadgesChanged = badges::refresh,
                showMessage = showMessage,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Leaves a flow (booking, payment) for a tab, dropping the flow's screens from the back stack. */
private fun NavHostController.resetToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

private fun NavBackStackEntry.requiredLongArg(name: String): Long =
    arguments?.getLong(name) ?: error("Missing navigation argument $name")

private fun NavBackStackEntry.longArg(name: String): Long? =
    arguments?.getLong(name, NO_ID)?.takeIf { it != NO_ID }

private fun NavBackStackEntry.stringArg(name: String): String = arguments?.getString(name).orEmpty()

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startRoute: String,
    container: AppContainer,
    sessionState: SessionState,
    unreadNotifications: Int,
    unreadChat: Int,
    onBadgesChanged: () -> Unit,
    showMessage: (UiText) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val session = (sessionState as? SessionState.LoggedIn)?.session
    val back: () -> Unit = { navController.popBackStack() }
    val openLogin: () -> Unit = { navController.navigate(Routes.LOGIN) }

    NavHost(navController = navController, startDestination = startRoute) {
        // ─── Guest ───────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container.hotelRepository, container.wishlistRepository, container.stayQuery) },
                onMessage = showMessage,
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenHotel = { navController.navigate(Routes.hotel(it)) },
                onOpenAssistant = { navController.navigate(Routes.assistant(null)) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel { SearchViewModel(container.hotelRepository, container.wishlistRepository, container.stayQuery) },
                onMessage = showMessage,
                onOpenHotel = { navController.navigate(Routes.hotel(it)) },
                onBack = back,
            )
        }
        composable(Routes.HOTEL, arguments = listOf(navArgument("hotelId") { type = NavType.LongType })) { entry ->
            val hotelId = entry.requiredLongArg("hotelId")
            HotelDetailScreen(
                viewModel = viewModel {
                    HotelDetailViewModel(hotelId, container.hotelRepository, container.catalogApi, container.wishlistRepository, container.sessionStore)
                },
                onMessage = showMessage,
                onBook = { roomId -> if (session == null) openLogin() else navController.navigate(Routes.book(hotelId, roomId)) },
                onAskAssistant = { navController.navigate(Routes.assistant(hotelId)) },
                onBack = back,
            )
        }
        composable(
            Routes.BOOK,
            arguments = listOf(
                navArgument("hotelId") { type = NavType.LongType },
                navArgument("roomId") {
                    type = NavType.LongType
                    defaultValue = NO_ID
                },
            ),
        ) { entry ->
            val hotelId = entry.requiredLongArg("hotelId")
            val roomId = entry.longArg("roomId")
            RequireLogin(sessionState, stringResource(R.string.booking_title), openLogin, back) {
                BookingFormScreen(
                    viewModel = viewModel {
                        BookingFormViewModel(
                            hotelId, roomId, container.hotelRepository, container.bookingApi, container.accountApi,
                            container.stayQuery, container.guestDetailsByBooking,
                        )
                    },
                    onMessage = showMessage,
                    onBooked = { bookingId ->
                        navController.navigate(Routes.payment(bookingId)) { popUpTo(Routes.BOOK) { inclusive = true } }
                    },
                    onBack = back,
                )
            }
        }
        composable(Routes.PAYMENT, arguments = listOf(navArgument("bookingId") { type = NavType.LongType })) { entry ->
            val bookingId = entry.requiredLongArg("bookingId")
            PaymentScreen(
                viewModel = viewModel { PaymentViewModel(bookingId, container.bookingApi) },
                onMessage = showMessage,
                onDone = { navController.navigate(Routes.confirmation(bookingId)) { popUpTo(Routes.PAYMENT) { inclusive = true } } },
                onPayLater = { navController.resetToTab(Routes.BOOKINGS) },
                onBack = back,
            )
        }
        composable(Routes.CONFIRMATION, arguments = listOf(navArgument("bookingId") { type = NavType.LongType })) { entry ->
            val bookingId = entry.requiredLongArg("bookingId")
            ConfirmationScreen(
                viewModel = viewModel {
                    ConfirmationViewModel(bookingId, container.bookingApi, container.guestDetailsByBooking[bookingId])
                },
                onViewBookings = { navController.resetToTab(Routes.BOOKINGS) },
                onHome = { navController.resetToTab(Routes.HOME) },
            )
        }
        composable(Routes.WISHLIST) {
            WishlistScreen(
                viewModel = viewModel { WishlistViewModel(container.hotelRepository, container.wishlistRepository) },
                onMessage = showMessage,
                onOpenHotel = { navController.navigate(Routes.hotel(it)) },
                onExplore = { navController.openTab(Routes.HOME) },
            )
        }
        composable(Routes.BOOKINGS) {
            RequireLogin(sessionState, stringResource(R.string.tab_bookings), openLogin, onBack = null) {
                MyBookingsScreen(
                    viewModel = viewModel { MyBookingsViewModel(container.bookingApi, appContext) },
                    onMessage = showMessage,
                    onPay = { navController.navigate(Routes.payment(it)) },
                    onOpenHotel = { navController.navigate(Routes.hotel(it)) },
                    onExplore = { navController.openTab(Routes.HOME) },
                )
            }
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(
                viewModel = viewModel { AccountViewModel(container.accountApi, container.authRepository, container.sessionStore, appContext) },
                sessionState = sessionState,
                unreadNotifications = unreadNotifications,
                unreadChat = unreadChat,
                onMessage = showMessage,
                actions = AccountActions(
                    onLogin = openLogin,
                    onRegister = { navController.navigate(Routes.REGISTER) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onProfileSettings = { navController.navigate(Routes.PROFILE) },
                    onSupportChat = { navController.navigate(Routes.SUPPORT_CHAT) },
                    onAssistant = { navController.navigate(Routes.assistant(null)) },
                    onManageUsers = { navController.navigate(Routes.ADMIN_USERS) },
                    onManageReviews = { navController.navigate(Routes.ADMIN_REVIEWS) },
                    onSupportInbox = { navController.navigate(Routes.ADMIN_INBOX) },
                ),
            )
        }
        composable(Routes.NOTIFICATIONS) {
            RequireLogin(sessionState, stringResource(R.string.notifications_title), openLogin, back) {
                NotificationsScreen(
                    viewModel = viewModel { NotificationsViewModel(container.accountApi, onBadgesChanged) },
                    onMessage = showMessage,
                    onBack = back,
                )
            }
        }
        composable(Routes.PROFILE) {
            RequireLogin(sessionState, stringResource(R.string.profile_settings_title), openLogin, back) {
                ProfileSettingsScreen(viewModel { ProfileSettingsViewModel(container.accountApi) }, showMessage, back)
            }
        }
        composable(Routes.SUPPORT_CHAT) {
            RequireLogin(sessionState, stringResource(R.string.support_chat_title), openLogin, back) {
                ConversationScreen(
                    viewModel = viewModel { GuestSupportChatViewModel(container.accountApi, container.supportChatSocket) },
                    title = stringResource(R.string.support_chat_title),
                    viewerIsAdmin = false,
                    onMessage = showMessage,
                    onBack = {
                        onBadgesChanged()
                        back()
                    },
                )
            }
        }
        composable(
            Routes.ASSISTANT,
            arguments = listOf(navArgument("hotelId") {
                type = NavType.LongType
                defaultValue = NO_ID
            }),
        ) { entry ->
            val hotelId = entry.longArg("hotelId")
            AssistantScreen(
                viewModel = viewModel {
                    AssistantViewModel(hotelId, container.accountApi, container.hotelRepository, container.sessionStore, appContext)
                },
                onBack = back,
            )
        }

        // ─── Authentication ──────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = viewModel { LoginViewModel(container.authRepository) },
                onMessage = showMessage,
                onLoggedIn = back,
                onRegister = { navController.navigate(Routes.REGISTER) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onBack = back,
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = viewModel { RegisterViewModel(container.authRepository) },
                onMessage = showMessage,
                onRegistered = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } } },
                onLogin = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } } },
                onBack = back,
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                viewModel = viewModel { ForgotPasswordViewModel(container.authRepository) },
                onMessage = showMessage,
                onBackToLogin = back,
            )
        }

        // ─── Admin ───────────────────────────────────────────
        composable(Routes.ADMIN_OVERVIEW) {
            AdminOnly(session?.isAdmin == true) {
                AdminOverviewScreen(viewModel { AdminOverviewViewModel(container.adminApi, appContext) }, showMessage)
            }
        }
        composable(Routes.ADMIN_BOOKINGS) {
            AdminOnly(session?.isAdmin == true) {
                AdminBookingsScreen(viewModel { AdminBookingsViewModel(container.adminApi) }, showMessage)
            }
        }
        composable(Routes.ADMIN_REQUESTS) {
            AdminOnly(session?.isAdmin == true) {
                AdminRequestsScreen(viewModel { AdminRequestsViewModel(container.adminApi) }, showMessage)
            }
        }
        composable(Routes.ADMIN_HOTELS) {
            AdminOnly(session?.isAdmin == true) {
                AdminHotelsScreen(
                    viewModel = viewModel { AdminHotelsViewModel(container.adminApi, container.hotelRepository, appContext) },
                    onMessage = showMessage,
                    onManageRooms = { navController.navigate(Routes.adminRooms(it.id, it.name.orEmpty())) },
                )
            }
        }
        composable(
            Routes.ADMIN_ROOMS,
            arguments = listOf(
                navArgument("hotelId") { type = NavType.LongType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val hotelId = entry.requiredLongArg("hotelId")
            AdminOnly(session?.isAdmin == true) {
                AdminRoomsScreen(
                    viewModel = viewModel {
                        AdminRoomsViewModel(hotelId, container.adminApi, container.catalogApi, container.hotelRepository, appContext)
                    },
                    hotelName = entry.stringArg("name"),
                    onMessage = showMessage,
                    onBack = back,
                )
            }
        }
        composable(Routes.ADMIN_USERS) {
            AdminOnly(session?.isAdmin == true) {
                AdminUsersScreen(viewModel { AdminUsersViewModel(container.adminApi, session?.username.orEmpty()) }, showMessage, back)
            }
        }
        composable(Routes.ADMIN_REVIEWS) {
            AdminOnly(session?.isAdmin == true) {
                AdminReviewsScreen(viewModel { AdminReviewsViewModel(container.adminApi) }, showMessage, back)
            }
        }
        composable(Routes.ADMIN_INBOX) {
            AdminOnly(session?.isAdmin == true) {
                AdminInboxScreen(
                    viewModel = viewModel { AdminInboxViewModel(container.adminApi, container.supportChatSocket) },
                    onMessage = showMessage,
                    onOpenRoom = { navController.navigate(Routes.adminChat(it.roomId, it.username.orEmpty())) },
                    onBack = {
                        onBadgesChanged()
                        back()
                    },
                )
            }
        }
        composable(
            Routes.ADMIN_CHAT,
            arguments = listOf(
                navArgument("roomId") { type = NavType.LongType },
                navArgument("username") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val roomId = entry.requiredLongArg("roomId")
            AdminOnly(session?.isAdmin == true) {
                ConversationScreen(
                    viewModel = viewModel { AdminConversationViewModel(roomId, container.adminApi, container.supportChatSocket) },
                    title = entry.stringArg("username"),
                    viewerIsAdmin = true,
                    onMessage = showMessage,
                    onBack = back,
                )
            }
        }
    }
}

/** Shows [content] to signed-in users and a sign-in prompt to everyone else. */
@Composable
private fun RequireLogin(
    sessionState: SessionState,
    title: String,
    onLogin: () -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (sessionState is SessionState.LoggedIn) {
        content()
        return
    }
    Scaffold(topBar = { AppTopBar(title, onBack = onBack) }) { padding ->
        EmptyState(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.login_required_title),
            body = stringResource(R.string.login_required_body),
            actionLabel = stringResource(R.string.auth_login_title),
            onAction = onLogin,
            modifier = Modifier.padding(padding),
        )
    }
}

/** Admin screens stay blank for a moment while a sign-out swaps the tabs back to the guest ones. */
@Composable
private fun AdminOnly(isAdmin: Boolean, content: @Composable () -> Unit) {
    if (isAdmin) content() else LoadingBox()
}
