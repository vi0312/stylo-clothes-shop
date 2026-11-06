package com.stylo.shop.feature.profile.data

import com.stylo.shop.feature.auth.data.UserDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ProfileApi {

    @PATCH("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserDto

    @GET("me/addresses")
    suspend fun listAddresses(): AddressListDto

    @POST("me/addresses")
    suspend fun createAddress(@Body body: CreateAddressRequest): AddressDto

    @PATCH("me/addresses/{id}")
    suspend fun updateAddress(
        @Path("id") id: Long,
        @Body body: UpdateAddressRequest,
    ): AddressDto

    @DELETE("me/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: Long)
}

/** The server treats an empty phone as a clear, matching the nullable PATCH /me contract. */
@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val phone: String,
)

@Serializable
data class CreateAddressRequest(
    val recipientName: String,
    val phone: String,
    val line: String,
    val isDefault: Boolean,
)

@Serializable
data class UpdateAddressRequest(
    val recipientName: String,
    val phone: String,
    val line: String,
    val isDefault: Boolean? = null,
)

@Serializable
data class AddressDto(
    val id: Long,
    val recipientName: String,
    val phone: String,
    val line: String,
    val isDefault: Boolean,
)

@Serializable
data class AddressListDto(
    val items: List<AddressDto>,
)
