package com.stylo.shop.feature.admin.ui.products

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stylo.shop.BuildConfig
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiEffectHost
import com.stylo.shop.feature.admin.model.ADMIN_COLOR_MAX
import com.stylo.shop.feature.admin.model.ADMIN_DESCRIPTION_MAX
import com.stylo.shop.feature.admin.model.ADMIN_NAME_MAX
import com.stylo.shop.feature.admin.model.ADMIN_SIZE_MAX
import com.stylo.shop.feature.admin.model.ADMIN_SKU_MAX
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminVariant
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.ImageUpload
import com.stylo.shop.feature.admin.model.MAX_PRODUCT_IMAGES
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.admin.ui.AdminErrorBanner
import com.stylo.shop.feature.admin.ui.adminFieldErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.stylo.shop.core.ui.components.StyloCard
import com.stylo.shop.core.ui.components.StyloConfirmDialog
import com.stylo.shop.core.ui.components.StyloErrorState
import com.stylo.shop.core.ui.components.StyloBusyButton
import androidx.compose.foundation.layout.heightIn

@Composable
fun AdminProductFormScreen(
    productId: Long?,
    onBack: () -> Unit,
    viewModel: AdminProductFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val upload = withContext(Dispatchers.IO) { readImage(context, uri) }
                if (upload != null) {
                    viewModel.uploadImage(upload)
                } else {
                    viewModel.onImageReadFailed()
                }
            }
        }
    }

    UiEffectHost(effects = viewModel.effects, snackbarHostState = snackbarHostState)

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    AdminDetailScaffold(
        title = stringResource(
            if (productId == null) R.string.admin_product_new_title else R.string.admin_product_edit_title,
        ),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.loadError != null -> {
                StyloErrorState(
                    error = state.loadError!!,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                ProductFormContent(
                    state = state,
                    padding = padding,
                    onNameChange = viewModel::onNameChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onCategoryChange = viewModel::onCategoryChange,
                    onBrandChange = viewModel::onBrandChange,
                    onStatusChange = viewModel::onStatusChange,
                    onSave = viewModel::save,
                    onAddVariant = viewModel::openCreateVariant,
                    onEditVariant = viewModel::openEditVariant,
                    onRequestDeleteVariant = viewModel::requestDeleteVariant,
                    onPickImage = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRetryImage = viewModel::retryImage,
                    onDismissFailedImage = viewModel::dismissFailedImage,
                    onRequestDeleteImage = viewModel::requestDeleteImage,
                    onRequestDeleteProduct = viewModel::requestDeleteProduct,
                )
            }
        }
    }

    state.variantEditor?.let { editor ->
        VariantEditorDialog(
            editor = editor,
            onSkuChange = viewModel::onVariantSkuChange,
            onSizeChange = viewModel::onVariantSizeChange,
            onColorChange = viewModel::onVariantColorChange,
            onPriceChange = viewModel::onVariantPriceChange,
            onSave = viewModel::saveVariant,
            onDismiss = viewModel::dismissVariantEditor,
        )
    }

    state.confirmDeleteVariant?.let { variant ->
        StyloConfirmDialog(
            title = stringResource(R.string.admin_variant_delete_title),
            body = stringResource(R.string.admin_variant_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            busy = state.isDeletingVariant,
            onConfirm = viewModel::confirmDeleteVariant,
            onDismiss = viewModel::dismissDeleteVariant,
        )
    }

    state.confirmDeleteImage?.let {
        StyloConfirmDialog(
            title = stringResource(R.string.admin_image_delete_title),
            body = stringResource(R.string.admin_image_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            busy = state.isDeletingImage,
            onConfirm = viewModel::confirmDeleteImage,
            onDismiss = viewModel::dismissDeleteImage,
        )
    }

    if (state.showDeleteProduct) {
        StyloConfirmDialog(
            title = stringResource(R.string.admin_product_delete_title),
            body = stringResource(R.string.admin_product_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            busy = state.isDeletingProduct,
            onConfirm = viewModel::confirmDeleteProduct,
            onDismiss = viewModel::dismissDeleteProduct,
        )
    }
}

@Composable
private fun ProductFormContent(
    state: AdminProductFormUiState,
    padding: PaddingValues,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onBrandChange: (Long) -> Unit,
    onStatusChange: (CatalogStatus) -> Unit,
    onSave: () -> Unit,
    onAddVariant: () -> Unit,
    onEditVariant: (AdminVariant) -> Unit,
    onRequestDeleteVariant: (AdminVariant) -> Unit,
    onPickImage: () -> Unit,
    onRetryImage: () -> Unit,
    onDismissFailedImage: () -> Unit,
    onRequestDeleteImage: (AdminProductImage) -> Unit,
    onRequestDeleteProduct: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StyloCard(title = stringResource(R.string.admin_product_section_info)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.admin_product_name)) },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let {
                        { Text(adminFieldErrorText(it, ADMIN_NAME_MAX)) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.admin_product_description)) },
                    isError = state.descriptionError != null,
                    supportingText = state.descriptionError?.let {
                        { Text(adminFieldErrorText(it, ADMIN_DESCRIPTION_MAX)) }
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                TaxonomyDropdown(
                    label = stringResource(R.string.admin_product_category),
                    items = state.categories,
                    selectedId = state.categoryId,
                    error = state.categoryError,
                    onSelect = onCategoryChange,
                )

                TaxonomyDropdown(
                    label = stringResource(R.string.admin_product_brand),
                    items = state.brands,
                    selectedId = state.brandId,
                    error = state.brandError,
                    onSelect = onBrandChange,
                )

                Text(
                    text = stringResource(R.string.admin_product_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CatalogStatus.entries.forEachIndexed { index, status ->
                        SegmentedButton(
                            selected = state.status == status,
                            onClick = { onStatusChange(status) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CatalogStatus.entries.size,
                            ),
                            label = {
                                Text(
                                    stringResource(
                                        if (status == CatalogStatus.ACTIVE) {
                                            R.string.admin_filter_active
                                        } else {
                                            R.string.admin_filter_inactive
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                state.formError?.let { AdminErrorBanner(it) }
            }
        }

        if (state.productId == null) {
            Text(
                text = stringResource(R.string.admin_product_save_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (state.created) {
                Text(
                    text = stringResource(R.string.admin_product_created_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            ImagesSection(
                images = state.images,
                uploading = state.imageUploading,
                hasFailedImage = state.failedImage != null,
                onPickImage = onPickImage,
                onRetry = onRetryImage,
                onDismissFailed = onDismissFailedImage,
                onRequestDeleteImage = onRequestDeleteImage,
            )

            VariantsSection(
                variants = state.variants,
                onAddVariant = onAddVariant,
                onEditVariant = onEditVariant,
                onRequestDeleteVariant = onRequestDeleteVariant,
            )

            Spacer(Modifier.height(4.dp))
            StyloBusyButton(
                text = stringResource(R.string.admin_product_save),
                busy = state.saving,
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRequestDeleteProduct,
                enabled = !state.saving,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(stringResource(R.string.admin_product_delete))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxonomyDropdown(
    label: String,
    items: List<AdminTaxonomy>,
    selectedId: Long?,
    error: AdminFieldError?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = items.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(adminFieldErrorText(it)) } },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (item.status == CatalogStatus.INACTIVE) {
                                item.name + " (" + stringResource(R.string.admin_filter_inactive) + ")"
                            } else {
                                item.name
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(item.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImagesSection(
    images: List<AdminProductImage>,
    uploading: Boolean,
    hasFailedImage: Boolean,
    onPickImage: () -> Unit,
    onRetry: () -> Unit,
    onDismissFailed: () -> Unit,
    onRequestDeleteImage: (AdminProductImage) -> Unit,
) {
    StyloCard(title = stringResource(R.string.admin_product_section_images)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(images, key = { it.id }) { image ->
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.medium),
                ) {
                    val url = ApiClient.absoluteUrl(BuildConfig.API_BASE_URL, image.url)
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (image.position == 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.admin_image_cover),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        IconButton(
                            onClick = { onRequestDeleteImage(image) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(
                                    R.string.admin_image_delete_position,
                                    image.position + 1,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (images.size < MAX_PRODUCT_IMAGES) {
                item {
                    Surface(
                        onClick = onPickImage,
                        enabled = !uploading,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(3f / 4f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (uploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.admin_image_add),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uploading) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.admin_image_uploading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (hasFailedImage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.admin_image_upload_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.admin_image_retry))
                }
                TextButton(onClick = onDismissFailed) {
                    Text(stringResource(R.string.admin_image_dismiss))
                }
            }
        } else if (images.size >= MAX_PRODUCT_IMAGES) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.admin_image_limit, MAX_PRODUCT_IMAGES),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.admin_image_order_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantsSection(
    variants: List<AdminVariant>,
    onAddVariant: () -> Unit,
    onEditVariant: (AdminVariant) -> Unit,
    onRequestDeleteVariant: (AdminVariant) -> Unit,
) {
    StyloCard(title = stringResource(R.string.admin_product_section_variants)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.admin_product_stock_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddVariant) {
                Text(stringResource(R.string.admin_variant_add))
            }
        }

        if (variants.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_variant_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            variants.forEach { variant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditVariant(variant) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = variant.sku,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = variant.size + " · " + variant.color,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatters.money(variant.price),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.admin_variant_stock, variant.stock),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRequestDeleteVariant(variant) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun VariantEditorDialog(
    editor: VariantEditorState,
    onSkuChange: (String) -> Unit,
    onSizeChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.variantId == null) {
                        R.string.admin_variant_title_new
                    } else {
                        R.string.admin_variant_title_edit
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = editor.sku,
                    onValueChange = onSkuChange,
                    label = { Text(stringResource(R.string.admin_variant_sku)) },
                    isError = editor.skuError != null,
                    supportingText = editor.skuError?.let {
                        { Text(adminFieldErrorText(it, ADMIN_SKU_MAX)) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.size,
                    onValueChange = onSizeChange,
                    label = { Text(stringResource(R.string.admin_variant_size)) },
                    isError = editor.sizeError != null,
                    supportingText = editor.sizeError?.let {
                        { Text(adminFieldErrorText(it, ADMIN_SIZE_MAX)) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.color,
                    onValueChange = onColorChange,
                    label = { Text(stringResource(R.string.admin_variant_color)) },
                    isError = editor.colorError != null,
                    supportingText = editor.colorError?.let {
                        { Text(adminFieldErrorText(it, ADMIN_COLOR_MAX)) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.price,
                    onValueChange = onPriceChange,
                    label = { Text(stringResource(R.string.admin_variant_price)) },
                    isError = editor.priceError != null,
                    supportingText = editor.priceError?.let {
                        { Text(adminFieldErrorText(it)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                editor.formError?.let { AdminErrorBanner(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !editor.saving) {
                if (editor.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.saving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun readImage(context: Context, uri: Uri): ImageUpload? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "image/jpeg"
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null

    val extension = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return ImageUpload(bytes = bytes, fileName = "product.$extension", mimeType = mimeType)
}


