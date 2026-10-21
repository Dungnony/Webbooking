package com.nhom2.booking.ui.admin

import com.nhom2.booking.data.model.Room
import com.nhom2.booking.data.model.RoomBody
import com.nhom2.booking.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminDraftsTest {

    @Test
    fun roomDraftBuildsATrimmedBody() {
        val draft = RoomDraft(" Deluxe ", " Double ", "1500000", "2", "3", "  ")

        assertEquals(RoomBody("Deluxe", "Double", 1_500_000.0, 2, 3, null), draft.toBody())
    }

    @Test
    fun roomDraftRejectsMissingOrNonPositiveNumbers() {
        val valid = RoomDraft("A", "B", "100", "2", "1", "")

        assertNull(valid.copy(price = "").toBody())
        assertNull(valid.copy(price = "0").toBody())
        assertNull(valid.copy(capacity = "0").toBody())
        assertNull(valid.copy(quantity = "x").toBody())
        assertNull(valid.copy(name = " ").toBody())
        assertNotNull(valid.toBody())
    }

    @Test
    fun roomDraftRoundTripsAnExistingRoom() {
        val room = Room(id = 7, name = "Suite", type = "King", pricePerNight = 2_500_000.0, capacity = 4, quantity = 2, description = "View")

        assertEquals(RoomBody("Suite", "King", 2_500_000.0, 4, 2, "View"), RoomDraft.from(room).toBody())
    }

    @Test
    fun hotelDraftNeedsEveryField() {
        assertTrue(HotelDraft("A", "B", "C", "D").isComplete)
        assertFalse(HotelDraft("A", "", "C", "D").isComplete)
    }

    @Test
    fun userDraftNeedsAPasswordOnlyForNewAccounts() {
        val draft = UserDraft("alice", "alice@example.com", "", UserRole.USER)

        assertNotNull(draft.validationError(isNew = true))
        assertNull(draft.validationError(isNew = false))
        assertNotNull(draft.copy(password = "weak").validationError(isNew = false))
        assertNull(draft.copy(password = "Strong1!").validationError(isNew = true))
        assertNotNull(draft.copy(email = "nope").validationError(isNew = false))
    }
}
