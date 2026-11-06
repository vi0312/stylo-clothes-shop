package com.stylo.shop.feature.admin

import com.stylo.shop.feature.admin.data.AdminPaginatedProductsDto
import com.stylo.shop.feature.admin.data.AdminPaginatedTaxonomyDto
import com.stylo.shop.feature.admin.data.AdminProductDetailDto
import com.stylo.shop.feature.admin.data.AdminProductImageDto
import com.stylo.shop.feature.admin.data.AdminProductSummaryDto
import com.stylo.shop.feature.admin.data.AdminRefDto
import com.stylo.shop.feature.admin.data.AdminTaxonomyDto
import com.stylo.shop.feature.admin.data.AdminVariantDto
import com.stylo.shop.feature.admin.data.toAdminProductDetail
import com.stylo.shop.feature.admin.data.toAdminProductSummary
import com.stylo.shop.feature.admin.data.toAdminTaxonomy
import com.stylo.shop.feature.admin.data.toPaginatedProducts
import com.stylo.shop.feature.admin.data.toPaginatedTaxonomy
import com.stylo.shop.feature.admin.model.CatalogStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminCatalogMapperTest {

    private val category = AdminRefDto(id = 1, name = "Áo")
    private val brand = AdminRefDto(id = 2, name = "Stylo")

    @Test
    fun summaryMapsStatusAndStockStats() {
        val dto = AdminProductSummaryDto(
            id = 7,
            name = "Áo polo",
            description = "Mô tả",
            status = "INACTIVE",
            category = category,
            brand = brand,
            coverUrl = "/uploads/products/a.jpg",
            variantCount = 3,
            totalStock = 4,
            minPrice = 199_000,
            maxPrice = 259_000,
        )

        val model = dto.toAdminProductSummary()

        assertEquals(7L, model.id)
        assertEquals(CatalogStatus.INACTIVE, model.status)
        assertEquals(3, model.variantCount)
        assertEquals(4, model.totalStock)
        assertTrue(model.isLowStock)
        assertEquals("Áo", model.category.name)
        assertEquals("Stylo", model.brand.name)
    }

    @Test
    fun detailSortsImagesByPositionAndVariantsById() {
        val dto = AdminProductDetailDto(
            id = 5,
            name = "Áo",
            status = "ACTIVE",
            category = category,
            brand = brand,
            images = listOf(
                AdminProductImageDto(id = 3, url = "/uploads/3.jpg", position = 2),
                AdminProductImageDto(id = 1, url = "/uploads/1.jpg", position = 0),
                AdminProductImageDto(id = 2, url = "/uploads/2.jpg", position = 1),
            ),
            variants = listOf(
                AdminVariantDto(id = 9, productId = 5, sku = "B", size = "L", color = "Đen", price = 1, stock = 1),
                AdminVariantDto(id = 4, productId = 5, sku = "A", size = "M", color = "Đen", price = 2, stock = 2),
            ),
        )

        val model = dto.toAdminProductDetail()

        assertEquals(listOf(0, 1, 2), model.images.map { it.position })
        assertEquals(listOf(4L, 9L), model.variants.map { it.id })
    }

    @Test
    fun taxonomyMapsProductCount() {
        val dto = AdminTaxonomyDto(
            id = 3,
            name = "Quần",
            status = "INACTIVE",
            productCount = 6,
        )

        val model = dto.toAdminTaxonomy()

        assertEquals(3L, model.id)
        assertEquals(CatalogStatus.INACTIVE, model.status)
        assertEquals(6, model.productCount)
    }

    @Test
    fun paginationComputesHasMore() {
        val first = AdminPaginatedProductsDto(
            items = emptyList(),
            page = 1,
            limit = 20,
            total = 45,
        ).toPaginatedProducts()
        val last = AdminPaginatedProductsDto(
            items = emptyList(),
            page = 3,
            limit = 20,
            total = 45,
        ).toPaginatedProducts()

        assertTrue(first.hasMore)
        assertFalse(last.hasMore)
    }

    @Test
    fun taxonomyPaginationCarriesItems() {
        val page = AdminPaginatedTaxonomyDto(
            items = listOf(AdminTaxonomyDto(id = 1, name = "Áo", status = "ACTIVE", productCount = 2)),
            page = 2,
            limit = 20,
            total = 41,
        ).toPaginatedTaxonomy()

        assertEquals(1, page.items.size)
        assertEquals(2, page.page)
        assertTrue(page.hasMore)
    }
}
