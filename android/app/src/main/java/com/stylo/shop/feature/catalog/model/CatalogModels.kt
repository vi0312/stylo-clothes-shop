package com.stylo.shop.feature.catalog.model

data class CategoryItem(
    val id: Long,
    val name: String,
)

data class BrandItem(
    val id: Long,
    val name: String,
)

data class ProductCard(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val minPrice: Long?,
    val maxPrice: Long?,
    val category: CategoryItem,
    val brand: BrandItem,
)

data class ProductImage(
    val id: Long,
    val url: String,
    val position: Int,
)

data class ProductVariant(
    val id: Long,
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
    val stock: Int,
)

data class ProductDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val category: CategoryItem,
    val brand: BrandItem,
    val images: List<ProductImage>,
    val variants: List<ProductVariant>,
    val minPrice: Long?,
    val maxPrice: Long?,
)

data class PriceRange(
    val min: Long?,
    val max: Long?,
)

data class CatalogFilters(
    val categories: List<CategoryItem>,
    val brands: List<BrandItem>,
    val colors: List<String>,
    val sizes: List<String>,
    val priceRange: PriceRange,
)

data class CatalogQuery(
    val q: String? = null,
    val categoryId: Long? = null,
    val brandId: Long? = null,
    val color: String? = null,
    val size: String? = null,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val sort: String = "newest",
    val page: Int = 1,
    val limit: Int = 20,
)

data class PaginatedProducts(
    val items: List<ProductCard>,
    val page: Int,
    val limit: Int,
    val total: Int,
) {
    val totalPages: Int
        get() = if (limit > 0) ((total + limit - 1) / limit).coerceAtLeast(1) else 1

    val hasMore: Boolean
        get() = page < totalPages
}

enum class CatalogSortOption(val id: String, val labelResId: Int) {
    NEWEST("newest", com.stylo.shop.R.string.catalog_sort_newest),
    PRICE_ASC("price_asc", com.stylo.shop.R.string.catalog_sort_price_asc),
    PRICE_DESC("price_desc", com.stylo.shop.R.string.catalog_sort_price_desc),
    NAME_ASC("name_asc", com.stylo.shop.R.string.catalog_sort_name_asc);

    companion object {
        fun fromId(id: String): CatalogSortOption =
            entries.find { it.id == id } ?: NEWEST
    }
}
