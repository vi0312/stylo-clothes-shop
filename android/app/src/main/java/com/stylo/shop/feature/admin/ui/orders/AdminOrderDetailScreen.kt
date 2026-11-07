package com.stylo.shop.feature.admin.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiEffectHost
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.admin.model.AdminOrderDetail
import com.stylo.shop.feature.admin.model.AdminOrderItem
import com.stylo.shop.feature.admin.model.AdminPayment
import com.stylo.shop.feature.admin.model.PaymentAttemptStatus
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.PaymentStatus
import com.stylo.shop.feature.orders.ui.OrderStatusBadge
import com.stylo.shop.feature.orders.ui.orderStatusLabel

@Composable
fun AdminOrderDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminOrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingTarget by remember { mutableStateOf<OrderStatus?>(null) }

    UiEffectHost(viewModel.effects, snackbarHostState)

    AdminDetailScaffold(
        title = stringResource(R.string.admin_order_detail_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when (val orderState = state.orderState) {
            is UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(ErrorMessages.resourceId(orderState.error)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::retry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            is UiState.Content -> AdminOrderContent(
                order = orderState.data,
                allowedTargets = state.allowedTargets,
                isUpdating = state.isUpdating,
                onRequestTransition = { pendingTarget = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }

    val target = pendingTarget
    val order = state.order
    if (target != null && order != null) {
        AlertDialog(
            onDismissRequest = { pendingTarget = null },
            title = { Text(stringResource(R.string.admin_order_transition_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.admin_order_transition_body,
                        order.code,
                        orderStatusLabel(order.status),
                        orderStatusLabel(target),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingTarget = null
                        viewModel.transition(target)
                    },
                    modifier = Modifier.testTag(UiTags.AdminOrderTransitionConfirm),
                ) {
                    Text(stringResource(R.string.admin_order_transition_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AdminOrderContent(
    order: AdminOrderDetail,
    allowedTargets: List<OrderStatus>,
    isUpdating: Boolean,
    onRequestTransition: (OrderStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeaderCard(order) }
        item { CustomerCard(order) }
        item { SectionTitle(stringResource(R.string.order_detail_items_title)) }
        items(order.items, key = { it.id }) { AdminOrderLine(it) }
        item { AdminSummaryCard(order) }
        order.note?.takeIf { it.isNotBlank() }?.let { note ->
            item { AdminNoteCard(note) }
        }
        item {
            SectionTitle(stringResource(R.string.admin_order_payment_title))
        }
        if (order.payments.isEmpty()) {
            item {
                AdminCard {
                    Text(
                        text = stringResource(R.string.admin_order_payment_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(order.payments, key = { it.id }) { AdminPaymentRow(it) }
        }
        item { ActionsCard(allowedTargets, isUpdating, onRequestTransition) }
    }
}

@Composable
private fun HeaderCard(order: AdminOrderDetail) {
    AdminCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = order.code,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTags.OrderDetailCode),
            )
            OrderStatusBadge(order.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.order_detail_created,
                Formatters.dateTime(order.createdAt),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = order.paymentMethod.apiValue + " · " + paymentStatusLabel(order.paymentStatus),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomerCard(order: AdminOrderDetail) {
    AdminCard {
        SectionTitle(stringResource(R.string.admin_order_customer_title))
        Text(text = order.customer.fullName, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = order.customer.email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        order.customer.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            Text(
                text = phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text(
            text = stringResource(R.string.order_detail_shipping_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = order.recipientName, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = order.recipientPhone,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = order.shippingAddress, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdminOrderLine(item: AdminOrderItem) {
    AdminCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = item.productName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${item.sku} · ${item.size}/${item.color}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.admin_order_cost_label,
                        item.unitCost?.let(Formatters::money) ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.money(item.lineTotal),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "× ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdminSummaryCard(order: AdminOrderDetail) {
    AdminCard {
        MoneyRow(stringResource(R.string.order_detail_subtotal), order.subtotal)
        MoneyRow(stringResource(R.string.order_detail_shipping_fee), order.shippingFee)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.order_detail_total),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Formatters.money(order.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AdminNoteCard(note: String) {
    AdminCard {
        SectionTitle(stringResource(R.string.admin_order_note_title))
        Text(text = note, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdminPaymentRow(payment: AdminPayment) {
    AdminCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = payment.txnRef, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = payment.provider,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                payment.failureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PaymentStatusBadge(payment.status)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Formatters.money(payment.amount),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActionsCard(
    allowedTargets: List<OrderStatus>,
    isUpdating: Boolean,
    onRequestTransition: (OrderStatus) -> Unit,
) {
    AdminCard {
        SectionTitle(stringResource(R.string.admin_order_actions_title))
        if (allowedTargets.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_order_terminal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AdminCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allowedTargets.forEach { target ->
                if (target == OrderStatus.CANCELLED) {
                    OutlinedButton(
                        onClick = { onRequestTransition(target) },
                        enabled = !isUpdating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTags.adminOrderTransition(target.name)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(stringResource(R.string.admin_order_action_cancel))
                    }
                } else {
                    Button(
                        onClick = { onRequestTransition(target) },
                        enabled = !isUpdating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTags.adminOrderTransition(target.name)),
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(actionLabel(target))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun actionLabel(target: OrderStatus): String = stringResource(
    when (target) {
        OrderStatus.CONFIRMED -> R.string.admin_order_action_confirm
        OrderStatus.SHIPPING -> R.string.admin_order_action_ship
        OrderStatus.DELIVERED -> R.string.admin_order_action_deliver
        else -> R.string.admin_order_action_cancel
    },
)

@Composable
private fun paymentStatusLabel(status: PaymentStatus): String = stringResource(
    when (status) {
        PaymentStatus.UNPAID -> R.string.order_detail_payment_unpaid
        PaymentStatus.PAID -> R.string.order_detail_payment_paid
        PaymentStatus.FAILED -> R.string.order_detail_payment_failed
    },
)

@Composable
private fun PaymentStatusBadge(status: PaymentAttemptStatus) {
    val colors = MaterialTheme.statusColors
    val pair = when (status) {
        PaymentAttemptStatus.PENDING -> colors.warning to colors.warningContainer
        PaymentAttemptStatus.SUCCEEDED -> colors.success to colors.successContainer
        PaymentAttemptStatus.FAILED ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }
    androidx.compose.material3.Surface(
        color = pair.second,
        contentColor = pair.first,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = stringResource(
                when (status) {
                    PaymentAttemptStatus.PENDING -> R.string.admin_payment_status_pending
                    PaymentAttemptStatus.SUCCEEDED -> R.string.admin_payment_status_succeeded
                    PaymentAttemptStatus.FAILED -> R.string.admin_payment_status_failed
                },
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AdminCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun MoneyRow(label: String, amount: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(text = Formatters.money(amount), style = MaterialTheme.typography.bodyMedium)
    }
}
