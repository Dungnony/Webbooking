package com.nhom2.booking.data.api

import com.nhom2.booking.data.model.AiChatBody
import com.nhom2.booking.data.model.AiReply
import com.nhom2.booking.data.model.Booking
import com.nhom2.booking.data.model.BookingRequestItem
import com.nhom2.booking.data.model.ChangeEmailBody
import com.nhom2.booking.data.model.ChatRoomSummary
import com.nhom2.booking.data.model.CountResponse
import com.nhom2.booking.data.model.CreateBookingBody
import com.nhom2.booking.data.model.CreateBookingRequestBody
import com.nhom2.booking.data.model.CreateReviewBody
import com.nhom2.booking.data.model.Hotel
import com.nhom2.booking.data.model.HotelBody
import com.nhom2.booking.data.model.ImageUploadResponse
import com.nhom2.booking.data.model.NotificationItem
import com.nhom2.booking.data.model.PaymentQr
import com.nhom2.booking.data.model.ProcessRequestBody
import com.nhom2.booking.data.model.Review
import com.nhom2.booking.data.model.ReviewEligibility
import com.nhom2.booking.data.model.ReviewPage
import com.nhom2.booking.data.model.ReviewReplyBody
import com.nhom2.booking.data.model.Room
import com.nhom2.booking.data.model.RoomBody
import com.nhom2.booking.data.model.SendMessageBody
import com.nhom2.booking.data.model.SupportChatHistory
import com.nhom2.booking.data.model.SupportMessage
import com.nhom2.booking.data.model.UpdateProfileBody
import com.nhom2.booking.data.model.UserBody
import com.nhom2.booking.data.model.UserProfile
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Public catalogue: hotels, rooms and reviews. Reading needs no login. */
interface CatalogApi {
    @GET("hotels")
    suspend fun getHotels(): List<Hotel>

    @GET("hotels/{id}")
    suspend fun getHotel(@Path("id") id: Long): Hotel

    @GET("rooms/hotel/{hotelId}")
    suspend fun getRooms(@Path("hotelId") hotelId: Long): List<Room>

    @GET("hotels/{hotelId}/reviews")
    suspend fun getReviews(@Path("hotelId") hotelId: Long, @Query("page") page: Int, @Query("size") size: Int): ReviewPage

    @GET("hotels/{hotelId}/review-eligibility")
    suspend fun getReviewEligibility(@Path("hotelId") hotelId: Long): ReviewEligibility

    @POST("api/reviews")
    suspend fun createReview(@Body body: CreateReviewBody): Review
}

interface BookingApi {
    @POST("bookings/{hotelId}")
    suspend fun create(@Path("hotelId") hotelId: Long, @Body body: CreateBookingBody): Booking

    @GET("bookings/my")
    suspend fun getMine(): List<Booking>

    @PUT("bookings/cancel/{bookingId}")
    suspend fun cancel(@Path("bookingId") bookingId: Long): Booking

    @POST("bookings/{bookingId}/requests")
    suspend fun createRequest(@Path("bookingId") bookingId: Long, @Body body: CreateBookingRequestBody): BookingRequestItem

    @GET("bookings/{bookingId}/requests")
    suspend fun getRequests(@Path("bookingId") bookingId: Long): List<BookingRequestItem>

    @Streaming
    @GET("bookings/{bookingId}/invoice")
    suspend fun downloadInvoice(@Path("bookingId") bookingId: Long): ResponseBody

    @GET("bookings/{bookingId}/payment-qr")
    suspend fun getPaymentQr(@Path("bookingId") bookingId: Long): PaymentQr

    @POST("bookings/{bookingId}/confirm-payment")
    suspend fun confirmPayment(@Path("bookingId") bookingId: Long): Booking
}

/** Everything about the signed-in user's own account. */
interface AccountApi {
    @GET("users/me")
    suspend fun getMe(): UserProfile

    @PUT("users/me")
    suspend fun changePassword(@Body body: UpdateProfileBody): UserProfile

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserProfile

    /** Returns the masked address the code was sent to. */
    @POST("users/me/email/otp")
    suspend fun requestEmailChange(@Body body: ChangeEmailBody): String

    @PUT("users/me/email")
    suspend fun confirmEmailChange(@Body body: ChangeEmailBody): UserProfile

    @GET("notifications/my")
    suspend fun getNotifications(): List<NotificationItem>

    @PUT("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Long): NotificationItem

    @DELETE("notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: Long)

    @GET("wishlist")
    suspend fun getWishlist(): List<Long>

    @PUT("wishlist/{hotelId}")
    suspend fun addToWishlist(@Path("hotelId") hotelId: Long): List<Long>

    @DELETE("wishlist/{hotelId}")
    suspend fun removeFromWishlist(@Path("hotelId") hotelId: Long): List<Long>

    @DELETE("wishlist")
    suspend fun clearWishlist()

    @POST("wishlist/merge")
    suspend fun mergeWishlist(@Body hotelIds: List<Long>): List<Long>

    @GET("api/support-chat/me")
    suspend fun getSupportChat(): SupportChatHistory

    @POST("api/support-chat/me/messages")
    suspend fun sendSupportMessage(@Body body: SendMessageBody): SupportMessage

    @POST("api/support-chat/me/read")
    suspend fun markSupportChatRead()

    @GET("api/support-chat/me/unread-count")
    suspend fun getSupportUnreadCount(): CountResponse

    @POST("api/chat")
    suspend fun askAssistant(@Body body: AiChatBody): AiReply
}

interface AdminApi {
    @GET("admin/statistics/overview")
    suspend fun getOverview(): JsonObject

    @Streaming
    @GET("admin/reports/bookings.xlsx")
    suspend fun exportBookings(): ResponseBody

    @GET("admin/bookings")
    suspend fun getBookings(@Query("status") status: String?): List<Booking>

    @PUT("admin/bookings/{id}/approve")
    suspend fun approveBooking(@Path("id") id: Long): Booking

    @PUT("admin/bookings/{id}/reject")
    suspend fun rejectBooking(@Path("id") id: Long, @Body body: ProcessRequestBody): Booking

    @PUT("admin/bookings/{id}/paid")
    suspend fun markBookingPaid(@Path("id") id: Long): Booking

    @GET("admin/booking-requests")
    suspend fun getBookingRequests(@Query("status") status: String?): List<BookingRequestItem>

    @PUT("admin/booking-requests/{id}/approve")
    suspend fun approveRequest(@Path("id") id: Long, @Body body: ProcessRequestBody): BookingRequestItem

    @PUT("admin/booking-requests/{id}/reject")
    suspend fun rejectRequest(@Path("id") id: Long, @Body body: ProcessRequestBody): BookingRequestItem

    @GET("admin/reviews")
    suspend fun getReviews(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("unanswered") unanswered: Boolean,
    ): ReviewPage

    @PUT("admin/reviews/{id}/reply")
    suspend fun replyToReview(@Path("id") id: Long, @Body body: ReviewReplyBody): Review

    @DELETE("admin/reviews/{id}/reply")
    suspend fun removeReviewReply(@Path("id") id: Long): Review

    @POST("hotels")
    suspend fun createHotel(@Body body: HotelBody): Hotel

    @PUT("hotels/{id}")
    suspend fun updateHotel(@Path("id") id: Long, @Body body: HotelBody): Hotel

    @DELETE("hotels/{id}")
    suspend fun deleteHotel(@Path("id") id: Long): String

    @POST("rooms/{hotelId}")
    suspend fun createRoom(@Path("hotelId") hotelId: Long, @Body body: RoomBody): Room

    @PUT("rooms/{id}")
    suspend fun updateRoom(@Path("id") id: Long, @Body body: RoomBody): Room

    @DELETE("rooms/{id}")
    suspend fun deleteRoom(@Path("id") id: Long): String

    @Multipart
    @POST("api/images/hotel/{hotelId}")
    suspend fun uploadHotelImage(@Path("hotelId") hotelId: Long, @Part file: MultipartBody.Part): ImageUploadResponse

    @Multipart
    @POST("api/images/room/{roomId}")
    suspend fun uploadRoomImage(@Path("roomId") roomId: Long, @Part file: MultipartBody.Part): ImageUploadResponse

    @GET("users")
    suspend fun getUsers(): List<UserProfile>

    @POST("users")
    suspend fun createUser(@Body body: UserBody): UserProfile

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body body: UserBody): UserProfile

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): String

    @GET("admin/support-chat/rooms")
    suspend fun getChatRooms(): List<ChatRoomSummary>

    @GET("admin/support-chat/rooms/{roomId}/messages")
    suspend fun getChatMessages(@Path("roomId") roomId: Long): List<SupportMessage>

    @POST("admin/support-chat/rooms/{roomId}/messages")
    suspend fun replyInChat(@Path("roomId") roomId: Long, @Body body: SendMessageBody): SupportMessage

    @POST("admin/support-chat/rooms/{roomId}/read")
    suspend fun markChatRead(@Path("roomId") roomId: Long)

    @GET("admin/support-chat/unread-count")
    suspend fun getChatUnreadCount(): CountResponse
}
