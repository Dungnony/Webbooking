package com.nhom2.booking.data.model

import kotlinx.serialization.Serializable

// Mirrors the JSON the backend returns. Fields are optional because older rows in the shared database have gaps.
// Dates arrive as ISO strings: LocalDate "2026-10-20", LocalDateTime "2026-10-20T09:30:00.123".

object BookingStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val REJECTED = "REJECTED"
    const val CANCELLED = "CANCELLED"
}

object PaymentStatus {
    const val UNPAID = "UNPAID"
    const val PAID = "PAID"
    const val REFUNDED = "REFUNDED"
}

object RequestStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
}

object RequestType {
    const val CANCEL = "CANCEL"
    const val CHANGE_DATE = "CHANGE_DATE"
}

object UserRole {
    const val USER = "USER"
    const val ADMIN = "ADMIN"
}

@Serializable
data class Hotel(
    val id: Long,
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val ratingAvg: Double? = null,
    val status: String? = null,
    val reviewCount: Long? = null,
    val createdAt: String? = null,
    val rooms: List<Room> = emptyList(),
)

@Serializable
data class Room(
    val id: Long,
    val name: String? = null,
    val type: String? = null,
    val pricePerNight: Double? = null,
    val capacity: Int? = null,
    val quantity: Int? = null,
    val description: String? = null,
    val images: List<RoomImage> = emptyList(),
)

@Serializable
data class RoomImage(val id: Long? = null, val imageUrl: String? = null)

@Serializable
data class UserProfile(
    val id: Long? = null,
    val username: String = "",
    val email: String? = null,
    val role: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class Booking(
    val id: Long,
    val checkInDate: String? = null,
    val checkOutDate: String? = null,
    val totalPrice: Double? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val createdAt: String? = null,
    val user: UserProfile? = null,
    val hotel: Hotel? = null,
    val bookingRooms: List<BookingRoom> = emptyList(),
)

@Serializable
data class BookingRoom(
    val id: Long? = null,
    val quantity: Int? = null,
    val price: Double? = null,
    val room: Room? = null,
)

@Serializable
data class BookingRequestItem(
    val id: Long,
    val type: String? = null,
    val newCheckIn: String? = null,
    val newCheckOut: String? = null,
    val reason: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val processedAt: String? = null,
    val adminResponse: String? = null,
    val booking: Booking? = null,
    val processedBy: UserProfile? = null,
)

@Serializable
data class PaymentQr(
    val bookingId: Long? = null,
    val qrCodeUrl: String? = null,
    val bankName: String? = null,
    val accountNumber: String? = null,
    val accountName: String? = null,
    val amount: Double? = null,
    val transferContent: String? = null,
)

@Serializable
data class NotificationItem(
    val id: Long,
    val title: String? = null,
    val message: String? = null,
    val read: Boolean? = null,
    val createdAt: String? = null,
)

@Serializable
data class Review(
    val id: Long,
    val rating: Int? = null,
    val comment: String? = null,
    val createdAt: String? = null,
    val username: String? = null,
    val hotelId: Long? = null,
    val hotelName: String? = null,
    val hotelReply: String? = null,
    val hotelRepliedAt: String? = null,
)

@Serializable
data class ReviewPage(
    val content: List<Review> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    /** Over all of the hotel's reviews, not just this page; null when there are none. */
    val averageRating: Double? = null,
)

@Serializable
data class ReviewEligibility(val canReview: Boolean = false, val reason: String? = null)

@Serializable
data class SupportMessage(
    val id: Long,
    val roomId: Long? = null,
    val senderUsername: String? = null,
    val fromAdmin: Boolean = false,
    val content: String = "",
    val createdAt: String? = null,
    val readAt: String? = null,
)

@Serializable
data class SupportChatHistory(
    /** Null until the guest sends their first message. */
    val roomId: Long? = null,
    val messages: List<SupportMessage> = emptyList(),
)

@Serializable
data class ChatRoomSummary(
    val roomId: Long,
    val username: String? = null,
    val lastMessageAt: String? = null,
    val lastMessagePreview: String? = null,
    val unreadCount: Long = 0,
)

@Serializable
data class CountResponse(val count: Long = 0)

@Serializable
data class ImageUploadResponse(val message: String? = null, val imageUrl: String? = null)

@Serializable
data class AiMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Serializable
data class AiHotelInfo(
    val name: String? = null,
    val description: String? = null,
    val address: String? = null,
    val city: String? = null,
    val ratingAvg: Double? = null,
)

@Serializable
data class AiReply(val reply: String? = null)

// ─── Request bodies ──────────────────────────────────────────

@Serializable
data class CreateBookingBody(val checkInDate: String, val checkOutDate: String, val rooms: List<BookingRoomBody>)

@Serializable
data class BookingRoomBody(val roomId: Long, val quantity: Int)

@Serializable
data class CreateBookingRequestBody(
    val type: String,
    val newCheckIn: String? = null,
    val newCheckOut: String? = null,
    val reason: String? = null,
)

@Serializable
data class ProcessRequestBody(val response: String? = null)

@Serializable
data class CreateReviewBody(val rating: Int, val comment: String, val hotel: IdRef)

@Serializable
data class IdRef(val id: Long)

@Serializable
data class ReviewReplyBody(val reply: String)

@Serializable
data class UpdateProfileBody(val newPassword: String)

@Serializable
data class ChangeEmailBody(val email: String, val otp: String? = null)

@Serializable
data class SendMessageBody(val content: String)

@Serializable
data class AiChatBody(val messages: List<AiMessage>, val hotelInfo: AiHotelInfo)

@Serializable
data class GoogleLoginBody(val credential: String)

/** The backend saves the whole hotel on update, so every field is sent back, not just the edited ones. */
@Serializable
data class HotelBody(
    val id: Long? = null,
    val name: String,
    val address: String,
    val city: String,
    val description: String,
    val imageUrl: String? = null,
    val ratingAvg: Double? = null,
    val status: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class RoomBody(
    val name: String,
    val type: String,
    val pricePerNight: Double,
    val capacity: Int,
    val quantity: Int,
    val description: String? = null,
)

/** An empty [password] on update keeps the current one (UserService.create). */
@Serializable
data class UserBody(
    val id: Long? = null,
    val username: String,
    val email: String,
    val password: String? = null,
    val role: String,
)
