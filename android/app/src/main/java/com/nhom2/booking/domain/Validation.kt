package com.nhom2.booking.domain

const val OTP_LENGTH = 6
private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 72

// Same pattern as UserService.EMAIL_PATTERN on the backend.
private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * Mirrors UserService.validatePasswordStrength on the backend: 8-72 characters, no whitespace, at least one
 * uppercase letter, one lowercase letter, one digit and one other character.
 */
fun isStrongPassword(password: String): Boolean {
    if (password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) return false
    if (password.any { it.isWhitespace() }) return false
    var hasUpper = false
    var hasLower = false
    var hasDigit = false
    var hasSpecial = false
    for (c in password) {
        when {
            c.isUpperCase() -> hasUpper = true
            c.isLowerCase() -> hasLower = true
            c.isDigit() -> hasDigit = true
            else -> hasSpecial = true
        }
    }
    return hasUpper && hasLower && hasDigit && hasSpecial
}

fun isValidEmail(email: String): Boolean = EMAIL_PATTERN.matches(email.trim())

fun isValidOtp(otp: String): Boolean = otp.length == OTP_LENGTH && otp.all { it.isDigit() }

// Vietnamese mobile and landline numbers, with or without +84 and common separators.
private val PHONE_PATTERN = Regex("^\\+?[0-9]{9,12}$")

fun isValidPhone(phone: String): Boolean = PHONE_PATTERN.matches(phone.filterNot { it == ' ' || it == '.' || it == '-' })
