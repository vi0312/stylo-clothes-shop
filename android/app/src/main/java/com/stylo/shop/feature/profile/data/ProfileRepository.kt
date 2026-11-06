package com.stylo.shop.feature.profile.data

import com.stylo.shop.core.model.User
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.auth.data.toUser
import com.stylo.shop.feature.profile.model.Address
import com.stylo.shop.feature.profile.model.AddressInput
import javax.inject.Inject

interface ProfileRepository {
    suspend fun updateProfile(fullName: String, phone: String): ApiResult<User>
    suspend fun listAddresses(): ApiResult<List<Address>>
    suspend fun createAddress(input: AddressInput): ApiResult<Address>
    suspend fun updateAddress(id: Long, input: AddressInput): ApiResult<Address>
    suspend fun deleteAddress(id: Long): ApiResult<Unit>
}

class DefaultProfileRepository @Inject constructor(
    private val api: ProfileApi,
) : ProfileRepository {

    override suspend fun updateProfile(fullName: String, phone: String): ApiResult<User> = apiCall {
        api.updateProfile(UpdateProfileRequest(fullName = fullName, phone = phone)).toUser()
    }

    override suspend fun listAddresses(): ApiResult<List<Address>> = apiCall {
        api.listAddresses().items.map(AddressDto::toAddress)
    }

    override suspend fun createAddress(input: AddressInput): ApiResult<Address> = apiCall {
        api.createAddress(
            CreateAddressRequest(
                recipientName = input.recipientName,
                phone = input.phone,
                line = input.line,
                isDefault = input.isDefault,
            ),
        ).toAddress()
    }

    override suspend fun updateAddress(id: Long, input: AddressInput): ApiResult<Address> = apiCall {
        api.updateAddress(
            id,
            UpdateAddressRequest(
                recipientName = input.recipientName,
                phone = input.phone,
                line = input.line,
                isDefault = input.isDefault.takeIf { it },
            ),
        ).toAddress()
    }

    override suspend fun deleteAddress(id: Long): ApiResult<Unit> = apiCall {
        api.deleteAddress(id)
    }
}

private fun AddressDto.toAddress(): Address = Address(
    id = id,
    recipientName = recipientName,
    phone = phone,
    line = line,
    isDefault = isDefault,
)
