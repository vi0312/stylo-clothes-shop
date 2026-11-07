package com.stylo.shop.feature.checkout

import com.stylo.shop.feature.checkout.data.devicePaymentUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentUrlTest {

    @Test
    fun `a mock url is re-anchored on the origin the device talks to`() {
        assertEquals(
            "http://10.0.2.2:8787/api/v1/payments/mock/STY261104032000123",
            devicePaymentUrl(
                paymentUrl = "http://127.0.0.1:8787/api/v1/payments/mock/STY261104032000123",
                provider = "MOCK",
                apiBaseUrl = "http://10.0.2.2:8787/api/v1/",
            ),
        )
    }

    @Test
    fun `the query string survives the move`() {
        assertEquals(
            "http://10.0.2.2:8787/api/v1/payments/mock/STY1?result=success",
            devicePaymentUrl(
                paymentUrl = "http://127.0.0.1:8787/api/v1/payments/mock/STY1?result=success",
                provider = "MOCK",
                apiBaseUrl = "http://10.0.2.2:8787/api/v1/",
            ),
        )
    }

    @Test
    fun `a gateway url is used exactly as issued`() {
        val vnpay = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=STY1"
        assertEquals(vnpay, devicePaymentUrl(vnpay, provider = "VNPAY", apiBaseUrl = "http://10.0.2.2:8787/api/v1/"))
    }

    @Test
    fun `no payment means no url`() {
        assertNull(devicePaymentUrl(null, provider = "MOCK", apiBaseUrl = "http://10.0.2.2:8787/api/v1/"))
    }
}
