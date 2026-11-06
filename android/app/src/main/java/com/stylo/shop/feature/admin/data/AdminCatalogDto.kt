package com.stylo.shop.feature.admin.data

import com.stylo.shop.feature.admin.model.AdminProductDetail
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.AdminRef
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminVariant
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.Paginated
import kotlinx.serialization.Serializable

@Serializable
data class AdminRefDto(
    val id: Long,
    val name: String,
)

@Serializable
data class AdminProductSummaryDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val status: String,
    val category: AdminRefDto,
    val brand: AdminRefDto,
    val coverUrl: String? = null,
    val variantCount: Int = 0,
    val totalStock: Int = 0,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
)

@Serializable
data class AdminProductImageDto(
    val id: Long,
    val url: String,
    val position: Int = 0,
)

@Serializable
data class AdminVariantDto(
    val id: Long,
    val productId: Long = 0,
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
    val stock: Int = 0,
)

@Serializable
data class AdminProductDetailDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val status: String,
    val category: AdminRefDto,
    val brand: AdminRefDto,
    val images: List<AdminProductImageDto> = emptyList(),
    val variants: List<AdminVariantDto> = emptyList(),
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
)

@Serializable
data class AdminTaxonomyDto(
    val id: Long,
    val name: String,
    val status: String,
    val productCount: Int = 0,
)

@Serializable
data class AdminPaginatedProductsDto(
    val items: List<AdminProductSummaryDto>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

@Serializable
data class AdminPaginatedTaxonomyDto(
    val items: List<AdminTaxonomyDto>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

@Serializable
data class CreateProductRequest(
    val name: String,
    val description: String? = null,
    val categoryId: Long,
    val brandId: Long,
    val status: String,
)

@Serializable
data class UpdateProductRequest(
    val name: String? = null,
    val description: String? = null,
    val categoryId: Long? = null,
    val brandId: Long? = null,
    val status: String? = null,
)

@Serializable
data class CreateVariantRequest(
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
)

@Serializable
data class UpdateVariantRequest(
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
)

@Serializable
data class CreateTaxonomyRequest(val name: String)

@Serializable
data class UpdateTaxonomyRequest(
    val name: String? = null,
    val status: String? = null,
)

fun AdminRefDto.toAdminRef() = AdminRef(id = id, name = name)

fun AdminProductSummaryDto.toAdminProductSummary() = AdminProductSummary(
    id = id,
    name = name,
    description = description,
    status = CatalogStatus.fromApi(status),
    category = category.toAdminRef(),
    brand = brand.toAdminRef(),
    coverUrl = coverUrl,
    variantCount = variantCount,
    totalStock = totalStock,
    minPrice = minPrice,
    maxPrice = maxPrice,
)

fun AdminProductImageDto.toAdminImage() = AdminProductImage(
    id = id,
    url = url,
    position = position,
)

fun AdminVariantDto.toAdminVariant() = AdminVariant(
    id = id,
    productId = productId,
    sku = sku,
    size = size,
    color = color,
    price = price,
    stock = stock,
)

fun AdminProductDetailDto.toAdminProductDetail() = AdminProductDetail(
    id = id,
    name = name,
    description = description,
    status = CatalogStatus.fromApi(status),
    category = category.toAdminRef(),
    brand = brand.toAdminRef(),
    images = images.map { it.toAdminImage() }.sortedBy { it.position },
    variants = variants.map { it.toAdminVariant() }.sortedBy { it.id },
    minPrice = minPrice,
    maxPrice = maxPrice,
)

fun AdminTaxonomyDto.toAdminTaxonomy() = AdminTaxonomy(
    id = id,
    name = name,
    status = CatalogStatus.fromApi(status),
    productCount = productCount,
)

fun AdminPaginatedProductsDto.toPaginatedProducts() = Paginated(
    items = items.map { it.toAdminProductSummary() },
    page = page,
    limit = limit,
    total = total,
)

fun AdminPaginatedTaxonomyDto.toPaginatedTaxonomy() = Paginated(
    items = items.map { it.toAdminTaxonomy() },
    page = page,
    limit = limit,
    total = total,
)
