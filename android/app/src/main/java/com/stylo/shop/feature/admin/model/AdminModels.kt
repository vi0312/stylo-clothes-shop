package com.stylo.shop.feature.admin.model

/** Products, categories and brands share the same two-state visibility model. */
enum class CatalogStatus(val apiValue: String) {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    ;

    companion object {
        fun fromApi(value: String): CatalogStatus =
            entries.firstOrNull { it.apiValue == value } ?: ACTIVE
    }
}

enum class AdminFieldError {
    REQUIRED,
    TOO_LONG,
    INVALID_NUMBER,
    INVALID_PRICE,
    SKU_TAKEN,
    VARIANT_EXISTS,
    NAME_TAKEN,
}

data class AdminRef(
    val id: Long,
    val name: String,
)

data class AdminProductSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val status: CatalogStatus,
    val category: AdminRef,
    val brand: AdminRef,
    val coverUrl: String?,
    val variantCount: Int,
    val totalStock: Int,
    val minPrice: Long?,
    val maxPrice: Long?,
) {
    val isLowStock: Boolean
        get() = totalStock <= LOW_STOCK_THRESHOLD
}

data class AdminProductImage(
    val id: Long,
    val url: String,
    val position: Int,
)

data class AdminVariant(
    val id: Long,
    val productId: Long,
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
    val stock: Int,
)

data class AdminProductDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val status: CatalogStatus,
    val category: AdminRef,
    val brand: AdminRef,
    val images: List<AdminProductImage>,
    val variants: List<AdminVariant>,
    val minPrice: Long?,
    val maxPrice: Long?,
) {
    val coverPosition: Int
        get() = images.minOfOrNull { it.position } ?: 0
}

data class AdminTaxonomy(
    val id: Long,
    val name: String,
    val status: CatalogStatus,
    val productCount: Int,
)

data class Paginated<T>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int,
) {
    val hasMore: Boolean
        get() = page * limit < total
}

data class AdminProductQuery(
    val q: String? = null,
    val status: CatalogStatus? = null,
    val categoryId: Long? = null,
    val brandId: Long? = null,
    val sort: String = "newest",
    val page: Int = 1,
    val limit: Int = 20,
)

data class AdminTaxonomyQuery(
    val q: String? = null,
    val status: CatalogStatus? = null,
    val sort: String = "newest",
    val page: Int = 1,
    val limit: Int = 20,
)

data class CreateProductInput(
    val name: String,
    val description: String?,
    val categoryId: Long,
    val brandId: Long,
    val status: CatalogStatus,
)

data class UpdateProductInput(
    val name: String? = null,
    val description: String? = null,
    val categoryId: Long? = null,
    val brandId: Long? = null,
    val status: CatalogStatus? = null,
)

data class CreateVariantInput(
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
)

data class UpdateVariantInput(
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
)

data class CreateTaxonomyInput(val name: String)

data class UpdateTaxonomyInput(
    val name: String? = null,
    val status: CatalogStatus? = null,
)

/**
 * An image already read from the picker. Keeping bytes out of the platform Uri type lets the
 * ViewModel and tests stay on the JVM.
 */
class ImageUpload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

const val LOW_STOCK_THRESHOLD = 5
const val MAX_PRODUCT_IMAGES = 5
const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

const val ADMIN_NAME_MAX = 200
const val ADMIN_DESCRIPTION_MAX = 5000
const val ADMIN_SKU_MAX = 50
const val ADMIN_SIZE_MAX = 20
const val ADMIN_COLOR_MAX = 40
const val ADMIN_TAXONOMY_NAME_MAX = 100
