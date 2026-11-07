package com.stylo.shop.feature.orders.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.core.ui.components.StyloStatusBadge
import com.stylo.shop.core.ui.components.StyloTone

@Composable
fun orderStatusLabel(status: OrderStatus): String = stringResource(
    when (status) {
        OrderStatus.PENDING -> R.string.order_status_pending
        OrderStatus.CONFIRMED -> R.string.order_status_confirmed
        OrderStatus.SHIPPING -> R.string.order_status_shipping
        OrderStatus.DELIVERED -> R.string.order_status_delivered
        OrderStatus.CANCELLED -> R.string.order_status_cancelled
    },
)

/** Status colours come from the shared tone table, never as ad-hoc reds and greens. */
@Composable
fun OrderStatusBadge(status: OrderStatus) {
    StyloStatusBadge(
        text = orderStatusLabel(status),
        tone = when (status) {
            OrderStatus.PENDING -> StyloTone.Warning
            OrderStatus.CONFIRMED -> StyloTone.Info
            OrderStatus.SHIPPING -> StyloTone.Indigo
            OrderStatus.DELIVERED -> StyloTone.Success
            OrderStatus.CANCELLED -> StyloTone.Neutral
        },
    )
}

@Composable
fun paymentMethodLabel(method: OrderPaymentMethod): String = stringResource(
    when (method) {
        OrderPaymentMethod.COD -> R.string.order_detail_payment_cod
        OrderPaymentMethod.ONLINE -> R.string.order_detail_payment_online
    },
)
