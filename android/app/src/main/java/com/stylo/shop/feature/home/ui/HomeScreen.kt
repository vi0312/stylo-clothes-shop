package com.stylo.shop.feature.home.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.model.User
import com.stylo.shop.core.ui.StyloLockupHorizontal
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.message
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.ProductCard
import com.stylo.shop.feature.catalog.ui.components.ProductCardView

/**
 * Home layout A (docs/ui.md). Guests see the same page; the cart badge and greeting wait for a
 * session, and the guarded tabs live in the shell, not here.
 */
@Composable
fun HomeScreen(
    user: User?,
    onOpenProduct: (Long) -> Unit,
    onOpenCatalog: (categoryId: Long?, focusSearch: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories = (state.categoriesState as? UiState.Content)?.data.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        HomeHeader(
            user = user,
            onSearch = { onOpenCatalog(null, true) },
        )
        Spacer(Modifier.height(16.dp))
        HeroBanner(onOpenCollection = { onOpenCatalog(null, false) })
        Spacer(Modifier.height(20.dp))

        if (categories.isNotEmpty()) {
            CategoryChips(
                categories = categories,
                onSelect = { category -> onOpenCatalog(category.id, false) },
            )
            Spacer(Modifier.height(22.dp))
        }

        CarouselSection(
            state = state.carouselState,
            onRetry = viewModel::loadCarousel,
            onOpenProduct = onOpenProduct,
            onSeeAll = { onOpenCatalog(null, false) },
        )

        if (categories.isNotEmpty()) {
            FreeshipStrip()
            Spacer(Modifier.height(24.dp))
        }

        state.blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(24.dp))
            CategoryBlock(
                block = block,
                onRetry = { viewModel.loadBlock(block.category.id) },
                onOpenProduct = onOpenProduct,
                onSeeAll = { onOpenCatalog(block.category.id, false) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader(user: User?, onSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            StyloLockupHorizontal(
                modifier = Modifier.height(22.dp),
            )
            if (user != null) {
                Text(
                    text = stringResource(R.string.signed_in_greeting, user.fullName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(UiTags.HomeGreeting),
                )
            }
        }
        IconButton(
            onClick = onSearch,
            modifier = Modifier.testTag(UiTags.HomeSearch),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.home_search),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HeroBanner(onOpenCollection: () -> Unit) {
    val tones = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(1.15f)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(tones)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp),
        ) {
            Text(
                text = stringResource(R.string.home_hero_kicker),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenCollection,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_cta),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<CategoryItem>, onSelect: (CategoryItem) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = false,
                onClick = { onSelect(category) },
                label = {
                    Text(category.name, style = MaterialTheme.typography.labelLarge)
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
    }
}

@Composable
private fun CarouselSection(
    state: UiState<List<ProductCard>>,
    onRetry: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onSeeAll: () -> Unit,
) {
    when (state) {
        is UiState.Content -> {
            if (state.data.isEmpty()) return
            SectionHeader(
                title = stringResource(R.string.home_new_arrivals),
                action = stringResource(R.string.home_see_all),
                onAction = onSeeAll,
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                items(state.data, key = { it.id }) { product ->
                    ProductCardView(
                        product = product,
                        onClick = { onOpenProduct(product.id) },
                        modifier = Modifier
                            .width(172.dp)
                            .testTag(UiTags.ProductCard),
                    )
                }
            }
        }
        is UiState.Loading -> {
            SectionHeader(title = stringResource(R.string.home_new_arrivals))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(2) {
                    SkeletonCard(Modifier.weight(1f))
                }
            }
        }
        is UiState.Error -> {
            SectionHeader(title = stringResource(R.string.home_new_arrivals))
            SectionError(message = state.error.message(), onRetry = onRetry)
        }
    }
}

@Composable
private fun FreeshipStrip() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_freeship),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryBlock(
    block: HomeBlock,
    onRetry: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onSeeAll: () -> Unit,
) {
    when (val state = block.productsState) {
        is UiState.Content -> {
            if (state.data.isEmpty()) return
            SectionHeader(
                title = block.category.name,
                action = stringResource(R.string.home_see_all),
                onAction = onSeeAll,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                state.data.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { product ->
                            ProductCardView(
                                product = product,
                                onClick = { onOpenProduct(product.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(UiTags.ProductCard),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        is UiState.Loading -> {
            SectionHeader(title = block.category.name)
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(2) {
                            SkeletonCard(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        is UiState.Error -> {
            SectionHeader(title = block.category.name)
            SectionError(message = state.error.message(), onRetry = onRetry)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SectionError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(14.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(16.dp))
    }
}
