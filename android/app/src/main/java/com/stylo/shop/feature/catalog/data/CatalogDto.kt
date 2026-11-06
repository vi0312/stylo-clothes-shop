package com.stylo.shop.feature.catalog.data

import com.stylo.shop.feature.catalog.model.BrandItem
import com.stylo.shop.feature.catalog.model.CatalogFilters
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.PaginatedProducts
import com.stylo.shop.feature.catalog.model.PriceRange
import com.stylo.shop.feature.catalog.model.ProductCard
import com.stylo.shop.feature.catalog.model.ProductDetail
import com.stylo.shop.feature.catalog.model.ProductImage
import com.stylo.shop.feature.catalog.model.ProductVariant
import kotlinx.serialization.Serializable

@Serializable
data class CategoryRefDto(
    val id: Long,
    val name: String,
)

@Serializable
data class BrandRefDto(
    val id: Long,
    val name: String,
)

@Serializable
data class ProductCardDto(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val category: CategoryRefDto,
    val brand: BrandRefDto,
)

@Serializable
data class ProductImageDto(
    val id: Long,
    val url: String,
    val position: Int = 0,
)

@Serializable
data class VariantDto(
    val id: Long,
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
    val stock: Int,
)

@Serializable
data class ProductDetailDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val category: CategoryRefDto,
    val brand: BrandRefDto,
    val images: List<ProductImageDto> = emptyList(),
    val variants: List<VariantDto> = emptyList(),
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PriceRangeDto(
    val min: Long? = null,
    val max: Long? = null,
)

@Serializable
data class CatalogFiltersDto(
    val categories: List<CategoryRefDto> = emptyList(),
    val brands: List<BrandRefDto> = emptyList(),
    val colors: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val priceRange: PriceRangeDto = PriceRangeDto(),
)

@Serializable
data class PaginatedProductsDto(
    val items: List<ProductCardDto>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

fun CategoryRefDto.toModel() = CategoryItem(id = id, name = name)
fun BrandRefDto.toModel() = BrandItem(id = id, name = name)

fun ProductCardDto.toModel() = ProductCard(
    id = id,
    name = name,
    coverUrl = coverUrl,
    minPrice = minPrice,
    maxPrice = maxPrice,
    category = category.toModel(),
    brand = brand.toModel(),
)

fun ProductImageDto.toModel() = ProductImage(
    id = id,
    url = url,
    position = position,
)

fun VariantDto.toModel() = ProductVariant(
    id = id,
    sku = sku,
    size = size,
    color = color,
    price = price,
    stock = stock,
)

fun ProductDetailDto.toModel() = ProductDetail(
    id = id,
    name = name,
    description = description,
    category = category.toModel(),
    brand = brand.toModel(),
    images = images.sortedBy { it.position }.map { it.toModel() },
    variants = variants.map { it.toModel() },
    minPrice = minPrice,
    maxPrice = maxPrice,
)

fun CatalogFiltersDto.toModel() = CatalogFilters(
    categories = categories.map { it.toModel() },
    brands = brands.map { it.toModel() },
    colors = colors,
    sizes = sizes,
    priceRange = PriceRange(min = priceRange.min, max = priceRange.max),
)

fun PaginatedProductsDto.toModel() = PaginatedProducts(
    items = items.map { it.toModel() },
    page = page,
    limit = limit,
    total = total,
)
