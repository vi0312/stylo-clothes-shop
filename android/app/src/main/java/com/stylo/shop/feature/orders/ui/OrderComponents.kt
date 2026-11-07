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

/** Status colours live in the theme pair table, not as ad-hoc reds and greens. */
@Composable
fun OrderStatusBadge(status: OrderStatus) {
    val colors = MaterialTheme.statusColors
    val pair = when (status) {
        OrderStatus.PENDING -> colors.warning to colors.warningContainer
        OrderStatus.CONFIRMED -> colors.info to colors.infoContainer
        OrderStatus.SHIPPING -> colors.indigo to colors.indigoContainer
        OrderStatus.DELIVERED -> colors.success to colors.successContainer
        OrderStatus.CANCELLED -> colors.neutral to colors.neutralContainer
    }
    Surface(color = pair.second, contentColor = pair.first, shape = CircleShape) {
        Text(
            text = orderStatusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun paymentMethodLabel(method: OrderPaymentMethod): String = stringResource(
    when (method) {
        OrderPaymentMethod.COD -> R.string.order_detail_payment_cod
        OrderPaymentMethod.ONLINE -> R.string.order_detail_payment_online
    },
)
