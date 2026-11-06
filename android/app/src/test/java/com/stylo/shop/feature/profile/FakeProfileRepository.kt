package com.stylo.shop.feature.profile

import com.stylo.shop.testUser
import com.stylo.shop.core.model.User
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.profile.data.ProfileRepository
import com.stylo.shop.feature.profile.model.Address
import com.stylo.shop.feature.profile.model.AddressInput

val testDefaultAddress = Address(
    id = 1,
    recipientName = "Nguyễn Văn An",
    phone = "0901234567",
    line = "12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM",
    isDefault = true,
)

val testSecondAddress = Address(
    id = 2,
    recipientName = "Trần Thị Bình",
    phone = "0912345678",
    line = "99 Lê Lợi, Q.1, TP.HCM",
    isDefault = false,
)

class FakeProfileRepository(
    var updateProfileResult: ApiResult<User> = ApiResult.Success(testUser),
    var listResult: ApiResult<List<Address>> = ApiResult.Success(emptyList()),
    var createResult: ApiResult<Address> = ApiResult.Success(testSecondAddress),
    var updateResult: ApiResult<Address> = ApiResult.Success(testSecondAddress),
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : ProfileRepository {

    val updateProfileCalls = mutableListOf<Pair<String, String>>()
    val created = mutableListOf<AddressInput>()
    val updated = mutableListOf<Pair<Long, AddressInput>>()
    val deleted = mutableListOf<Long>()
    var listCalls = 0

    override suspend fun updateProfile(fullName: String, phone: String): ApiResult<User> {
        updateProfileCalls += fullName to phone
        return updateProfileResult
    }

    override suspend fun listAddresses(): ApiResult<List<Address>> {
        listCalls += 1
        return listResult
    }

    override suspend fun createAddress(input: AddressInput): ApiResult<Address> {
        created += input
        return createResult
    }

    override suspend fun updateAddress(id: Long, input: AddressInput): ApiResult<Address> {
        updated += id to input
        return updateResult
    }

    override suspend fun deleteAddress(id: Long): ApiResult<Unit> {
        deleted += id
        return deleteResult
    }
}
