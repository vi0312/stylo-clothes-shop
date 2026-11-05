package com.stylo.shop.core.ui

import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun `documented codes map to their own message`() {
        assertEquals(R.string.error_out_of_stock, ErrorMessages.forCode("OUT_OF_STOCK"))
        assertEquals(R.string.error_account_disabled, ErrorMessages.forCode("ACCOUNT_DISABLED"))
        assertEquals(R.string.error_invalid_order_state, ErrorMessages.forCode("INVALID_ORDER_STATE"))
    }

    @Test
    fun `unknown codes fall back to the generic message`() {
        assertEquals(R.string.error_generic, ErrorMessages.forCode("FUTURE_CODE"))
        assertEquals(
            R.string.error_generic,
            ErrorMessages.resourceId(ApiError(ApiErrorKind.HTTP, code = "FUTURE_CODE", httpStatus = 418)),
        )
    }

    @Test
    fun `transport failures use their own message`() {
        assertEquals(
            R.string.error_network,
            ErrorMessages.resourceId(ApiError(ApiErrorKind.NETWORK)),
        )
        assertEquals(
            R.string.error_timeout,
            ErrorMessages.resourceId(ApiError(ApiErrorKind.TIMEOUT)),
        )
        assertEquals(
            R.string.error_parse,
            ErrorMessages.resourceId(ApiError(ApiErrorKind.PARSE)),
        )
    }
}
