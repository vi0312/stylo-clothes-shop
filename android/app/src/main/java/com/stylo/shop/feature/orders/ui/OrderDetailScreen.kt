package com.stylo.shop.feature.orders.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.OrderItem
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.PaymentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCancelDialog by remember { mutableStateOf(false) }

    // A payment page runs outside the app: the screen can be first composed while the tab
    // covers it, so every resume re-reads the order instead of trusting the initial load.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val cancelError = state.cancelError
    val cancelErrorMessage = cancelError?.let { stringResource(ErrorMessages.resourceId(it)) }
    LaunchedEffect(cancelErrorMessage) {
        if (cancelErrorMessage != null) {
            snackbarHostState.showSnackbar(cancelErrorMessage)
            viewModel.dismissCancelError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.order_detail_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
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
            is UiState.Content -> OrderContent(
                order = orderState.data,
                canCancel = state.canCancel,
                isCancelling = state.isCancelling,
                onRefresh = viewModel::refresh,
                onCancel = { showCancelDialog = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }

    if (showCancelDialog) {
        val order = (state.orderState as? UiState.Content)?.data
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.order_cancel_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.order_cancel_body,
                        order?.code.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancel()
                    },
                    modifier = Modifier.testTag(UiTags.OrderCancelConfirm),
                ) {
                    Text(
                        text = stringResource(R.string.order_cancel_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.order_cancel_keep))
                }
            },
        )
    }
}

@Composable
private fun OrderContent(
    order: Order,
    canCancel: Boolean,
    isCancelling: Boolean,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { OrderHeader(order) }
        if (order.paymentMethod == OrderPaymentMethod.ONLINE) {
            item { PaymentBanner(order = order, onRefresh = onRefresh) }
        }
        item { StatusTimeline(order.status) }
        item { SectionTitle(stringResource(R.string.order_detail_items_title)) }
        items(order.items, key = { it.id }) { item -> OrderLine(item) }
        item { ShippingCard(order) }
        item { SummaryCard(order) }
        order.note?.takeIf { it.isNotBlank() }?.let { note ->
            item { NoteCard(note) }
        }
        if (canCancel) {
            item { CancelOrderButton(isCancelling = isCancelling, onCancel = onCancel) }
        }
    }
}

@Composable
private fun CancelOrderButton(isCancelling: Boolean, onCancel: () -> Unit) {
    OutlinedButton(
        onClick = onCancel,
        enabled = !isCancelling,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.OrderCancel),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
    ) {
        if (isCancelling) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(stringResource(R.string.order_cancel))
        }
    }
}

@Composable
private fun OrderHeader(order: Order) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.code,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(UiTags.OrderDetailCode),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.order_detail_created, Formatters.dateTime(order.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.testTag(UiTags.OrderDetailStatus)) {
            OrderStatusBadge(order.status)
        }
    }
}

@Composable
private fun PaymentBanner(
    order: Order,
    onRefresh: () -> Unit,
) {
    val colors = MaterialTheme.statusColors
    val (container, content, title, hint) = when (order.paymentStatus) {
        PaymentStatus.PAID -> PaymentBannerStyle(
            container = colors.successContainer,
            content = colors.onSuccessContainer,
            title = stringResource(R.string.order_detail_payment_paid),
            hint = stringResource(R.string.order_detail_payment_paid_hint),
        )
        PaymentStatus.FAILED -> PaymentBannerStyle(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            title = stringResource(R.string.order_detail_payment_failed),
            hint = stringResource(R.string.order_detail_payment_failed_hint),
        )
        PaymentStatus.UNPAID -> PaymentBannerStyle(
            container = colors.warningContainer,
            content = colors.onWarningContainer,
            title = stringResource(R.string.order_detail_payment_unpaid),
            hint = stringResource(R.string.order_detail_payment_unpaid_hint),
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.OrderDetailPayment),
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(text = hint, style = MaterialTheme.typography.bodySmall)
            if (order.paymentStatus != PaymentStatus.PAID) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.order_detail_refresh))
                }
            }
        }
    }
}

private data class PaymentBannerStyle(
    val container: Color,
    val content: Color,
    val title: String,
    val hint: String,
)

private val timelineSteps = listOf(
    OrderStatus.PENDING,
    OrderStatus.CONFIRMED,
    OrderStatus.SHIPPING,
    OrderStatus.DELIVERED,
)

@Composable
private fun StatusTimeline(status: OrderStatus) {
    val colors = MaterialTheme.statusColors
    val reached = timelineSteps.indexOf(status)

    SectionCard {
        Text(
            text = stringResource(R.string.order_detail_timeline_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(10.dp))
        if (status == OrderStatus.CANCELLED) {
            Text(
                text = stringResource(R.string.order_status_cancelled),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            timelineSteps.forEachIndexed { index, step ->
                val done = index <= reached
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                        if (index < timelineSteps.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(26.dp)
                                    .background(
                                        if (done && index < reached) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = orderStatusLabel(step),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (index == reached) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderLine(item: OrderItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.productName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.variant_color_size, item.color, item.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.item_quantity, item.quantity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = Formatters.money(item.lineTotal),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ShippingCard(order: Order) {
    SectionCard {
        Text(
            text = stringResource(R.string.order_detail_shipping_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = order.recipientName, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = order.recipientPhone,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = order.shippingAddress, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryCard(order: Order) {
    SectionCard {
        MoneyRow(
            label = stringResource(R.string.order_detail_subtotal),
            amount = order.subtotal,
        )
        MoneyRow(
            label = stringResource(R.string.order_detail_shipping_fee),
            amount = order.shippingFee,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag(UiTags.OrderDetailTotal),
        ) {
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
        Spacer(Modifier.height(10.dp))
        Text(
            text = paymentMethodLabel(order.paymentMethod),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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

@Composable
private fun NoteCard(note: String) {
    SectionCard {
        Text(
            text = stringResource(R.string.order_detail_note_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = note, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}
