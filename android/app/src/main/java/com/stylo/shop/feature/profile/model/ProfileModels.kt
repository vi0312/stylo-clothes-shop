package com.stylo.shop.feature.profile.model

const val PROFILE_NAME_MAX = 100
const val PROFILE_PHONE_MAX = 20
const val ADDRESS_LINE_MAX = 255

enum class ProfileFieldError {
    REQUIRED,
    TOO_LONG,
}

data class Address(
    val id: Long,
    val recipientName: String,
    val phone: String,
    val line: String,
    val isDefault: Boolean,
)

data class AddressInput(
    val recipientName: String,
    val phone: String,
    val line: String,
    val isDefault: Boolean,
)
