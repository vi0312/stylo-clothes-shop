package com.stylo.shop.feature.checkout.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.stylo.shop.feature.cart.model.Cart
import com.stylo.shop.feature.cart.model.CartItem
import com.stylo.shop.feature.checkout.model.CHECKOUT_NOTE_MAX
import com.stylo.shop.feature.checkout.model.StockShortfall
import com.stylo.shop.feature.checkout.model.SubmitError
import com.stylo.shop.feature.checkout.model.UnavailableLine
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.profile.model.Address

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onBackToCart: () -> Unit,
    onOrderPlaced: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddressPicker by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CheckoutEffect.Placed -> {
                    val opened = effect.paymentUrl?.let { openPaymentPage(context, it) }
                    if (opened == false) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.checkout_browser_missing),
                        )
                    }
                    onOrderPlaced(effect.orderId)
                }
            }
        }
    }

    // NavHost disposes this destination while the address form covers it, so the screen is
    // composed fresh on return while the ViewModel survives: every resume re-reads cart and
    // addresses instead of trusting state `remember` cannot carry across that gap.
    LifecycleResumeEffect(Unit) {
        if (!state.isSubmitting) viewModel.load(showLoading = false)
        onPauseOrDispose { }
    }

    val cart = state.cart
    val showBottomBar = !state.isLoading && state.loadError == null && cart?.isEmpty == false

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.checkout_title),
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
        bottomBar = {
            if (showBottomBar) {
                CheckoutBottomBar(
                    state = state,
                    onAddAddress = onAddAddress,
                    onSubmit = viewModel::submit,
                )
            }
        },
    ) { padding ->
        val loadError = state.loadError
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            loadError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(ErrorMessages.resourceId(loadError)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::retry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            cart == null || cart.isEmpty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.checkout_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBackToCart) {
                    Text(stringResource(R.string.checkout_back_to_cart))
                }
            }
            else -> CheckoutContent(
                state = state,
                cart = cart,
                onChangeAddress = { showAddressPicker = true },
                onAddAddress = onAddAddress,
                onSelectAddress = viewModel::selectAddress,
                onSelectPayment = viewModel::selectPaymentMethod,
                onNoteChange = viewModel::updateNote,
                onDismissError = viewModel::dismissError,
                onRetrySubmit = viewModel::submit,
                onBackToCart = onBackToCart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }

    if (showAddressPicker) {
        AddressPickerSheet(
            addresses = state.addresses,
            selectedAddressId = state.selectedAddressId,
            onSelect = { addressId ->
                viewModel.selectAddress(addressId)
                showAddressPicker = false
            },
            onAddNew = {
                showAddressPicker = false
                onAddAddress()
            },
            onDismiss = { showAddressPicker = false },
        )
    }
}

@Composable
private fun CheckoutContent(
    state: CheckoutUiState,
    cart: Cart,
    onChangeAddress: () -> Unit,
    onAddAddress: () -> Unit,
    onSelectAddress: (Long) -> Unit,
    onSelectPayment: (OrderPaymentMethod) -> Unit,
    onNoteChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onRetrySubmit: () -> Unit,
    onBackToCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // The CTA lives in the fixed bottom bar, so the list can be scrolled anywhere when a submit
    // fails: jump back to the error card instead of leaving it unseen at the end of the list.
    LaunchedEffect(state.submitError) {
        if (state.submitError != null) listState.animateScrollToItem(0)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.submitError?.let { error ->
            item {
                SubmitErrorSection(
                    error = error,
                    onBackToCart = onBackToCart,
                    onRetry = onRetrySubmit,
                    onDismiss = onDismissError,
                )
            }
        }
        item {
            AddressSection(
                address = state.selectedAddress,
                hasAddresses = state.addresses.isNotEmpty(),
                onChange = onChangeAddress,
                onAdd = onAddAddress,
            )
        }
        item {
            PaymentSection(
                selected = state.paymentMethod,
                onSelect = onSelectPayment,
            )
        }
        item {
            NoteSection(note = state.note, onNoteChange = onNoteChange)
        }
        item { SectionTitle(stringResource(R.string.checkout_items_title)) }
        items(cart.items, key = { it.id }) { item -> CheckoutLine(item) }
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
private fun AddressSection(
    address: Address?,
    hasAddresses: Boolean,
    onChange: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.checkout_address_title))
        SectionCard {
            if (address == null) {
                Text(
                    text = stringResource(R.string.checkout_address_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAdd,
                    modifier = Modifier.testTag(UiTags.CheckoutAddressAdd),
                ) {
                    Text(stringResource(R.string.checkout_address_add))
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = address.recipientName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = address.phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = address.line,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (hasAddresses) {
                        TextButton(
                            onClick = onChange,
                            modifier = Modifier.testTag(UiTags.CheckoutAddressChange),
                        ) {
                            Text(stringResource(R.string.checkout_address_change))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentSection(
    selected: OrderPaymentMethod,
    onSelect: (OrderPaymentMethod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.checkout_payment_title))
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaymentOption(
                title = stringResource(R.string.checkout_payment_cod),
                hint = stringResource(R.string.checkout_payment_cod_hint),
                selected = selected == OrderPaymentMethod.COD,
                tag = UiTags.CheckoutPaymentCod,
                onClick = { onSelect(OrderPaymentMethod.COD) },
            )
            PaymentOption(
                title = stringResource(R.string.checkout_payment_online),
                hint = stringResource(R.string.checkout_payment_online_hint),
                selected = selected == OrderPaymentMethod.ONLINE,
                tag = UiTags.CheckoutPaymentOnline,
                onClick = { onSelect(OrderPaymentMethod.ONLINE) },
            )
        }
    }
}

@Composable
private fun PaymentOption(
    title: String,
    hint: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.checkout_note_title))
            Spacer(Modifier.weight(1f))
            if (note.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.checkout_note_counter, note.length, CHECKOUT_NOTE_MAX),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTags.CheckoutNote),
            placeholder = { Text(stringResource(R.string.checkout_note_placeholder)) },
            minLines = 2,
            maxLines = 3,
        )
    }
}

@Composable
private fun CheckoutLine(item: CartItem) {
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
private fun SubmitErrorSection(
    error: SubmitError,
    onBackToCart: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.CheckoutError),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.checkout_error_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            when (error) {
                is SubmitError.Stock -> {
                    Text(
                        text = stringResource(R.string.checkout_error_stock),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error.lines.forEach { line -> StockLine(line) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBackToCart,
                        modifier = Modifier.testTag(UiTags.CheckoutBackToCart),
                    ) {
                        Text(stringResource(R.string.checkout_back_to_cart))
                    }
                }
                is SubmitError.Unavailable -> {
                    Text(
                        text = stringResource(R.string.checkout_error_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error.lines.forEach { line -> UnavailableTextLine(line) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBackToCart,
                        modifier = Modifier.testTag(UiTags.CheckoutBackToCart),
                    ) {
                        Text(stringResource(R.string.checkout_back_to_cart))
                    }
                }
                SubmitError.CartEmpty -> {
                    Text(
                        text = stringResource(R.string.checkout_error_cart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBackToCart,
                        modifier = Modifier.testTag(UiTags.CheckoutBackToCart),
                    ) {
                        Text(stringResource(R.string.checkout_back_to_cart))
                    }
                }
                is SubmitError.Other -> {
                    Text(
                        text = stringResource(ErrorMessages.resourceId(error.error)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_dismiss))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockLine(line: StockShortfall) {
    Text(
        text = stringResource(
            R.string.checkout_error_stock_line,
            line.sku,
            line.requested,
            line.available,
        ),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun UnavailableTextLine(line: UnavailableLine) {
    Text(
        text = stringResource(
            R.string.checkout_error_unavailable_line,
            line.sku,
            stringResource(
                if (line.reason == "PRODUCT_INACTIVE") {
                    R.string.checkout_reason_inactive
                } else {
                    R.string.checkout_reason_missing
                },
            ),
        ),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun CheckoutBottomBar(
    state: CheckoutUiState,
    onAddAddress: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.checkout_subtotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.money(state.cart?.subtotal ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.checkout_shipping_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (state.addresses.isEmpty()) {
                Button(
                    onClick = onAddAddress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.CheckoutAddressAdd),
                ) {
                    Text(stringResource(R.string.checkout_address_add))
                }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.CheckoutSubmit),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.checkout_submitting))
                    } else {
                        Text(
                            text = stringResource(
                                R.string.checkout_submit,
                                Formatters.money(state.cart?.subtotal ?: 0),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressPickerSheet(
    addresses: List<Address>,
    selectedAddressId: Long?,
    onSelect: (Long) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag(UiTags.CheckoutAddressPicker),
        ) {
            Text(
                text = stringResource(R.string.checkout_address_pick_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                addresses.forEach { address ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = address.id == selectedAddressId,
                                onClick = { onSelect(address.id) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 10.dp)
                            .testTag(UiTags.CheckoutAddressOption),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = address.id == selectedAddressId, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = address.recipientName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = address.line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (address.isDefault) {
                            Text(
                                text = stringResource(R.string.address_default_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = onAddNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.CheckoutAddressAddNew),
            ) {
                Text(stringResource(R.string.checkout_address_add_new))
            }
        }
    }
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

/** Custom Tabs keeps the payment page in-app so its resume is the checkout result screen. */
private fun openPaymentPage(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    return runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.recoverCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.isSuccess
}
