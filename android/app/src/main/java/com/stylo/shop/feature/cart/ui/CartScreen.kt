package com.stylo.shop.feature.cart.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stylo.shop.core.ui.e2eTagsAsResourceId
import com.stylo.shop.BuildConfig
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiEffectHost
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.cart.model.Cart
import com.stylo.shop.feature.cart.model.CartItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onShop: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }

    UiEffectHost(viewModel.effects, snackbarHostState)

    // Another screen may have changed the cart (e.g. add from product detail); refresh on return.
    LifecycleResumeEffect(Unit) {
        viewModel.load(showLoading = false)
        onPauseOrDispose { }
    }

    val cart = (state.cartState as? UiState.Content)?.data

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.cart_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(UiTags.CartBack),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (cart != null && !cart.isEmpty) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            enabled = !state.isMutating,
                            modifier = Modifier.testTag(UiTags.CartClear),
                        ) {
                            Text(stringResource(R.string.cart_clear_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (cart != null && !cart.isEmpty) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Button(
                        onClick = onCheckout,
                        enabled = !state.isMutating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .testTag(UiTags.CartCheckout),
                    ) {
                        Text(stringResource(R.string.cart_checkout))
                    }
                }
            }
        },
    ) { padding ->
        when (val cartState = state.cartState) {
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
                    text = stringResource(ErrorMessages.resourceId(cartState.error)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::retry,
                    modifier = Modifier.testTag(UiTags.CartRetry),
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            is UiState.Content -> {
                if (cartState.data.isEmpty) {
                    EmptyCart(
                        onShop = onShop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                } else {
                    CartList(
                        cart = cartState.data,
                        mutatingItemId = state.mutatingItemId,
                        onIncrease = viewModel::increase,
                        onDecrease = viewModel::decrease,
                        onRemove = viewModel::remove,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            modifier = Modifier.e2eTagsAsResourceId(),
            title = { Text(stringResource(R.string.cart_clear_title)) },
            text = { Text(stringResource(R.string.cart_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clear()
                    },
                    modifier = Modifier.testTag(UiTags.CartClearConfirm),
                ) {
                    Text(
                        text = stringResource(R.string.cart_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.testTag(UiTags.CartClearCancel),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CartList(
    cart: Cart,
    mutatingItemId: Long?,
    onIncrease: (CartItem) -> Unit,
    onDecrease: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.cart_summary_count, cart.itemCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(cart.items, key = { it.id }) { item ->
            CartLine(
                item = item,
                enabled = mutatingItemId == null,
                onIncrease = { onIncrease(item) },
                onDecrease = { onDecrease(item) },
                onRemove = { onRemove(item) },
            )
        }
        item { SubtotalCard(cart) }
    }
}

@Composable
private fun CartLine(
    item: CartItem,
    enabled: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.CartItem),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val imageUrl = item.imageUrl?.let {
                    ApiClient.absoluteUrl(BuildConfig.API_BASE_URL, it)
                }
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = item.productName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.CartItemName)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.available) {
                        UnavailableBadge()
                    }
                }
                Text(
                    text = stringResource(R.string.cart_color_size, item.color, item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Formatters.money(item.unitPrice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.available) {
                        Stepper(
                            quantity = item.quantity,
                            enabled = enabled,
                            canDecrease = item.quantity > 1,
                            canIncrease = item.quantity < item.availableStock,
                            onDecrease = onDecrease,
                            onIncrease = onIncrease,
                        )
                        if (item.quantity >= item.availableStock) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cart_stock_limit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.cart_quantity_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onRemove,
                        enabled = enabled,
                        modifier = Modifier.testTag(UiTags.CartItemRemove),
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(
    quantity: Int,
    enabled: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onDecrease,
            enabled = enabled && canDecrease,
            modifier = Modifier
                .size(48.dp)
                .testTag(UiTags.CartStepperMinus),
        ) {
            Text(text = "−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = onIncrease,
            enabled = enabled && canIncrease,
            modifier = Modifier
                .size(48.dp)
                .testTag(UiTags.CartStepperPlus),
        ) {
            Text(text = "+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun UnavailableBadge() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = stringResource(R.string.cart_unavailable),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SubtotalCard(cart: Cart) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cart_subtotal),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.money(cart.subtotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag(UiTags.CartSubtotal),
                )
            }
            Text(
                text = stringResource(R.string.cart_shipping_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCart(
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .testTag(UiTags.CartEmpty),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ShoppingCart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.cart_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.cart_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onShop,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag(UiTags.CartShop),
        ) {
            Text(stringResource(R.string.cart_shop))
        }
    }
}
