package com.stylo.shop.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.stylo.shop.BuildConfig
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.catalog.model.ProductDetail
import com.stylo.shop.feature.catalog.ui.components.ColorSelector
import com.stylo.shop.feature.catalog.ui.components.SizeSelector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToCart: (Long) -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(UiTags.ProductDetailBack),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            // Sticky CTA Bar
            if (state.productState is UiState.Content) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Giá",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = Formatters.money(state.currentPrice),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.testTag(UiTags.ProductDetailPrice),
                            )
                        }

                        Button(
                            onClick = {
                                val variant = state.selectedVariant
                                if (variant != null) {
                                    onAddToCart(variant.id)
                                    scope.launch {
                                        val product = (state.productState as? UiState.Content)?.data
                                        val productName = product?.name ?: ""
                                        snackbarHostState.showSnackbar(
                                            "Đã chọn: $productName - ${variant.color} - ${variant.size}. Tính năng giỏ hàng sẽ có ở Ticket 22.",
                                        )
                                    }
                                }
                            },
                            enabled = state.isCtaEnabled,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .defaultMinSize(minWidth = 140.dp)
                                .testTag(UiTags.ProductDetailCta),
                        ) {
                            Text(
                                text = state.ctaButtonText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val productState = state.productState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val messageRes = if (productState.error.code == "NOT_FOUND") {
                            R.string.catalog_detail_product_not_found
                        } else {
                            ErrorMessages.resourceId(productState.error)
                        }
                        Text(
                            text = stringResource(messageRes),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onBack) {
                                Text("Quay lại")
                            }
                            if (productState.error.code != "NOT_FOUND") {
                                Button(onClick = viewModel::onRetry) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                }

                is UiState.Content -> {
                    val product = productState.data
                    ProductDetailContent(
                        product = product,
                        state = state,
                        onColorSelected = viewModel::onSelectColor,
                        onSizeSelected = viewModel::onSelectSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDetailContent(
    product: ProductDetail,
    state: ProductDetailUiState,
    onColorSelected: (String) -> Unit,
    onSizeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 1:1 Gallery with dots indicator (docs/ui.md)
        GallerySection(product = product)

        Column(modifier = Modifier.padding(20.dp)) {
            // Brand & Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = product.brand.name.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = product.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Product Name
            Text(
                text = product.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(UiTags.ProductDetailName),
            )

            Spacer(Modifier.height(8.dp))

            // Price & CompareAt Price
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Formatters.money(state.currentPrice),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (product.maxPrice != null && product.maxPrice > state.currentPrice) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Formatters.money(product.maxPrice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Stock Status Badge / Text
            val statusColor = when {
                state.isLowStock -> MaterialTheme.statusColors.warning
                state.selectedVariant != null && state.selectedVariant.stock > 0 -> MaterialTheme.statusColors.success
                state.selectedVariant != null && state.selectedVariant.stock <= 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = state.stockText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = statusColor,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // Color Selector
            if (state.availableColors.isNotEmpty()) {
                ColorSelector(
                    colors = state.availableColors,
                    selectedColor = state.selectedColor,
                    onColorSelected = onColorSelected,
                )
                Spacer(Modifier.height(20.dp))
            }

            // Size Selector
            if (state.availableSizes.isNotEmpty()) {
                SizeSelector(
                    sizes = state.availableSizes,
                    selectedSize = state.selectedSize,
                    onSizeSelected = onSizeSelected,
                )
                Spacer(Modifier.height(20.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // Description
            Text(
                text = stringResource(R.string.catalog_detail_description),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = product.description?.takeIf { it.isNotBlank() } ?: "Chưa có mô tả chi tiết cho sản phẩm này.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun GallerySection(product: ProductDetail) {
    val images = product.images
    if (images.isNotEmpty()) {
        val pagerState = rememberPagerState(pageCount = { images.size })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val image = images[page]
                val imageUrl = ApiClient.absoluteUrl(BuildConfig.API_BASE_URL, image.url)
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Dots indicator at bottom center
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(images.size) { index ->
                        val isSelected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 18.dp else 6.dp, 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    } else {
        // 1:1 Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
