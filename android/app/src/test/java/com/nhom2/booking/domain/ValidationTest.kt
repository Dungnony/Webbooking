package com.nhom2.booking.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun strongPasswordNeedsAllFourCharacterKinds() {
        assertTrue(isStrongPassword("Abcdef1!"))
        assertFalse(isStrongPassword("abcdef1!"))
        assertFalse(isStrongPassword("ABCDEF1!"))
        assertFalse(isStrongPassword("Abcdefg!"))
        assertFalse(isStrongPassword("Abcdefg1"))
    }

    @Test
    fun strongPasswordLengthIsEightToSeventyTwo() {
        assertFalse(isStrongPassword("Abcde1!"))
        assertTrue(isStrongPassword("Aa1!" + "a".repeat(68)))
        assertFalse(isStrongPassword("Aa1!" + "a".repeat(69)))
    }

    @Test
    fun strongPasswordRejectsWhitespace() {
        assertFalse(isStrongPassword("Abc def1!"))
        assertFalse(isStrongPassword("Abcdef1!\t"))
    }

    @Test
    fun vietnameseLettersCountAsLetters() {
        // The backend classifies with Character.isUpperCase/isLowerCase, so "Đ" and "ư" are letters, not specials.
        assertFalse(isStrongPassword("Đươngabc1"))
        assertTrue(isStrongPassword("Đươngabc1@"))
    }

    @Test
    fun emailMatchesTheBackendPattern() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("  user@example.com  "))
        assertFalse(isValidEmail("user@example"))
        assertFalse(isValidEmail("user example@x.com"))
        assertFalse(isValidEmail("@example.com"))
    }

    @Test
    fun otpIsExactlySixDigits() {
        assertTrue(isValidOtp("012345"))
        assertFalse(isValidOtp("12345"))
        assertFalse(isValidOtp("1234567"))
        assertFalse(isValidOtp("12a456"))
    }
}
