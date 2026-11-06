package com.stylo.shop.feature.catalog

import com.stylo.shop.feature.catalog.data.BrandRefDto
import com.stylo.shop.feature.catalog.data.CatalogFiltersDto
import com.stylo.shop.feature.catalog.data.CategoryRefDto
import com.stylo.shop.feature.catalog.data.PaginatedProductsDto
import com.stylo.shop.feature.catalog.data.PriceRangeDto
import com.stylo.shop.feature.catalog.data.ProductCardDto
import com.stylo.shop.feature.catalog.data.ProductDetailDto
import com.stylo.shop.feature.catalog.data.ProductImageDto
import com.stylo.shop.feature.catalog.data.VariantDto
import com.stylo.shop.feature.catalog.data.toModel
import com.stylo.shop.feature.catalog.model.CatalogSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMapperTest {

    @Test
    fun productCardDtoMapsToModel() {
        val dto = ProductCardDto(
            id = 10,
            name = "Áo Polo",
            coverUrl = "/uploads/polo.jpg",
            minPrice = 250_000,
            maxPrice = 280_000,
            category = CategoryRefDto(id = 1, name = "Áo"),
            brand = BrandRefDto(id = 2, name = "Stylo"),
        )

        val model = dto.toModel()

        assertEquals(10L, model.id)
        assertEquals("Áo Polo", model.name)
        assertEquals("/uploads/polo.jpg", model.coverUrl)
        assertEquals(250_000L, model.minPrice)
        assertEquals(280_000L, model.maxPrice)
        assertEquals("Áo", model.category.name)
        assertEquals("Stylo", model.brand.name)
    }

    @Test
    fun productDetailDtoMapsToModelAndSortsImagesByPosition() {
        val dto = ProductDetailDto(
            id = 5,
            name = "Quần Jeans",
            description = "Jeans form suông",
            category = CategoryRefDto(id = 2, name = "Quần"),
            brand = BrandRefDto(id = 1, name = "Stylo"),
            images = listOf(
                ProductImageDto(id = 2, url = "/img2.jpg", position = 1),
                ProductImageDto(id = 1, url = "/img1.jpg", position = 0),
            ),
            variants = listOf(
                VariantDto(id = 1, sku = "J-29", size = "29", color = "Xanh", price = 450_000, stock = 10),
            ),
            minPrice = 450_000,
            maxPrice = 450_000,
        )

        val model = dto.toModel()

        assertEquals(5L, model.id)
        assertEquals("Quần Jeans", model.name)
        assertEquals(2, model.images.size)
        assertEquals(0, model.images[0].position)
        assertEquals("/img1.jpg", model.images[0].url)
        assertEquals(1, model.images[1].position)
        assertEquals("/img2.jpg", model.images[1].url)
        assertEquals(1, model.variants.size)
        assertEquals(10, model.variants[0].stock)
    }

    @Test
    fun catalogFiltersDtoMapsToModel() {
        val dto = CatalogFiltersDto(
            categories = listOf(CategoryRefDto(id = 1, name = "Áo")),
            brands = listOf(BrandRefDto(id = 1, name = "Stylo")),
            colors = listOf("Đen", "Trắng"),
            sizes = listOf("S", "M", "L"),
            priceRange = PriceRangeDto(min = 100_000, max = 500_000),
        )

        val model = dto.toModel()

        assertEquals(1, model.categories.size)
        assertEquals("Áo", model.categories[0].name)
        assertEquals(2, model.colors.size)
        assertEquals(3, model.sizes.size)
        assertEquals(100_000L, model.priceRange.min)
        assertEquals(500_000L, model.priceRange.max)
    }

    @Test
    fun paginatedProductsComputesTotalPagesAndHasMore() {
        val dto = PaginatedProductsDto(
            items = emptyList(),
            page = 1,
            limit = 20,
            total = 45,
        )

        val model = dto.toModel()

        assertEquals(3, model.totalPages)
        assertTrue(model.hasMore)

        val lastPageDto = PaginatedProductsDto(
            items = emptyList(),
            page = 3,
            limit = 20,
            total = 45,
        )
        val lastPage = lastPageDto.toModel()
        assertEquals(3, lastPage.totalPages)
        assertFalse(lastPage.hasMore)
    }

    @Test
    fun catalogSortOptionLookup() {
        assertEquals(CatalogSortOption.NEWEST, CatalogSortOption.fromId("newest"))
        assertEquals(CatalogSortOption.PRICE_ASC, CatalogSortOption.fromId("price_asc"))
        assertEquals(CatalogSortOption.PRICE_DESC, CatalogSortOption.fromId("price_desc"))
        assertEquals(CatalogSortOption.NAME_ASC, CatalogSortOption.fromId("name_asc"))
        assertEquals(CatalogSortOption.NEWEST, CatalogSortOption.fromId("unknown"))
    }
}
