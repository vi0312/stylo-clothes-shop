package com.stylo.shop.feature.admin

import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.data.AdminCatalogRepository
import com.stylo.shop.feature.admin.model.AdminProductDetail
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.AdminProductQuery
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.AdminRef
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminTaxonomyQuery
import com.stylo.shop.feature.admin.model.AdminVariant
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.CreateProductInput
import com.stylo.shop.feature.admin.model.CreateTaxonomyInput
import com.stylo.shop.feature.admin.model.CreateVariantInput
import com.stylo.shop.feature.admin.model.ImageUpload
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.UpdateProductInput
import com.stylo.shop.feature.admin.model.UpdateTaxonomyInput
import com.stylo.shop.feature.admin.model.UpdateVariantInput

class FakeAdminCatalogRepository : AdminCatalogRepository {

    var productsResult: (AdminProductQuery) -> ApiResult<Paginated<AdminProductSummary>> = {
        ApiResult.Success(
            Paginated(items = listOf(sampleSummary()), page = 1, limit = it.limit, total = 1),
        )
    }
    var productDetailResult: (Long) -> ApiResult<AdminProductDetail> = { id ->
        ApiResult.Success(sampleDetail(id = id))
    }
    var createProductResult: (CreateProductInput) -> ApiResult<AdminProductDetail> = { input ->
        ApiResult.Success(
            sampleDetail(
                id = 42,
                name = input.name,
                status = input.status,
                categoryId = input.categoryId,
                brandId = input.brandId,
            ),
        )
    }
    var updateProductResult: (Long, UpdateProductInput) -> ApiResult<AdminProductDetail> = { id, input ->
        ApiResult.Success(
            sampleDetail(
                id = id,
                name = input.name ?: "Áo",
                status = input.status ?: CatalogStatus.ACTIVE,
                categoryId = input.categoryId ?: 1,
                brandId = input.brandId ?: 2,
            ),
        )
    }
    var deleteProductResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var createVariantResult: (Long, CreateVariantInput) -> ApiResult<AdminVariant> = { productId, input ->
        ApiResult.Success(
            AdminVariant(
                id = 100,
                productId = productId,
                sku = input.sku,
                size = input.size,
                color = input.color,
                price = input.price,
                stock = 0,
            ),
        )
    }
    var updateVariantResult: (Long, UpdateVariantInput) -> ApiResult<AdminVariant> = { id, input ->
        ApiResult.Success(
            AdminVariant(
                id = id,
                productId = 5,
                sku = input.sku,
                size = input.size,
                color = input.color,
                price = input.price,
                stock = 4,
            ),
        )
    }
    var deleteVariantResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var uploadImageResult: (Long, ImageUpload) -> ApiResult<AdminProductImage> = { _, _ ->
        ApiResult.Success(AdminProductImage(id = 1, url = "/uploads/products/a.jpg", position = 0))
    }
    var deleteImageResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var categoriesResult: ApiResult<Paginated<AdminTaxonomy>> = ApiResult.Success(
        Paginated(items = listOf(sampleTaxonomy(id = 1, name = "Áo")), page = 1, limit = 20, total = 1),
    )
    var brandsResult: ApiResult<Paginated<AdminTaxonomy>> = ApiResult.Success(
        Paginated(items = listOf(sampleTaxonomy(id = 2, name = "Stylo")), page = 1, limit = 20, total = 1),
    )
    var createCategoryResult: (CreateTaxonomyInput) -> ApiResult<AdminTaxonomy> = { input ->
        ApiResult.Success(sampleTaxonomy(id = 10, name = input.name))
    }
    var updateCategoryResult: (Long, UpdateTaxonomyInput) -> ApiResult<AdminTaxonomy> = { id, input ->
        ApiResult.Success(sampleTaxonomy(id = id, name = input.name ?: "Áo", status = input.status ?: CatalogStatus.ACTIVE))
    }
    var deleteCategoryResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var createBrandResult: (CreateTaxonomyInput) -> ApiResult<AdminTaxonomy> = { input ->
        ApiResult.Success(sampleTaxonomy(id = 11, name = input.name))
    }
    var updateBrandResult: (Long, UpdateTaxonomyInput) -> ApiResult<AdminTaxonomy> = { id, input ->
        ApiResult.Success(sampleTaxonomy(id = id, name = input.name ?: "Stylo", status = input.status ?: CatalogStatus.ACTIVE))
    }
    var deleteBrandResult: ApiResult<Unit> = ApiResult.Success(Unit)

    val productQueries = mutableListOf<AdminProductQuery>()
    val categoryQueries = mutableListOf<AdminTaxonomyQuery>()
    val brandQueries = mutableListOf<AdminTaxonomyQuery>()
    var createProductCalls = 0
    var uploadImageCalls = 0
    var deleteProductCalls = 0
    val updateProductCalls = mutableListOf<Pair<Long, UpdateProductInput>>()

    override suspend fun listProducts(
        query: AdminProductQuery,
    ): ApiResult<Paginated<AdminProductSummary>> {
        productQueries += query
        return productsResult(query)
    }

    override suspend fun getProduct(id: Long): ApiResult<AdminProductDetail> = productDetailResult(id)

    override suspend fun createProduct(input: CreateProductInput): ApiResult<AdminProductDetail> {
        createProductCalls++
        return createProductResult(input)
    }

    override suspend fun updateProduct(
        id: Long,
        input: UpdateProductInput,
    ): ApiResult<AdminProductDetail> {
        updateProductCalls += id to input
        return updateProductResult(id, input)
    }

    override suspend fun deleteProduct(id: Long): ApiResult<Unit> {
        deleteProductCalls++
        return deleteProductResult
    }

    override suspend fun createVariant(
        productId: Long,
        input: CreateVariantInput,
    ): ApiResult<AdminVariant> = createVariantResult(productId, input)

    override suspend fun updateVariant(
        id: Long,
        input: UpdateVariantInput,
    ): ApiResult<AdminVariant> = updateVariantResult(id, input)

    override suspend fun deleteVariant(id: Long): ApiResult<Unit> = deleteVariantResult

    override suspend fun uploadImage(
        productId: Long,
        image: ImageUpload,
    ): ApiResult<AdminProductImage> {
        uploadImageCalls++
        return uploadImageResult(productId, image)
    }

    override suspend fun deleteImage(productId: Long, imageId: Long): ApiResult<Unit> =
        deleteImageResult

    override suspend fun listCategories(
        query: AdminTaxonomyQuery,
    ): ApiResult<Paginated<AdminTaxonomy>> {
        categoryQueries += query
        return categoriesResult
    }

    override suspend fun createCategory(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy> =
        createCategoryResult(input)

    override suspend fun updateCategory(
        id: Long,
        input: UpdateTaxonomyInput,
    ): ApiResult<AdminTaxonomy> = updateCategoryResult(id, input)

    override suspend fun deleteCategory(id: Long): ApiResult<Unit> = deleteCategoryResult

    override suspend fun listBrands(
        query: AdminTaxonomyQuery,
    ): ApiResult<Paginated<AdminTaxonomy>> {
        brandQueries += query
        return brandsResult
    }

    override suspend fun createBrand(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy> =
        createBrandResult(input)

    override suspend fun updateBrand(
        id: Long,
        input: UpdateTaxonomyInput,
    ): ApiResult<AdminTaxonomy> = updateBrandResult(id, input)

    override suspend fun deleteBrand(id: Long): ApiResult<Unit> = deleteBrandResult
}

fun sampleSummary(
    id: Long = 1,
    name: String = "Áo thun cotton",
    status: CatalogStatus = CatalogStatus.ACTIVE,
    totalStock: Int = 20,
    variantCount: Int = 2,
) = AdminProductSummary(
    id = id,
    name = name,
    description = null,
    status = status,
    category = AdminRef(1, "Áo"),
    brand = AdminRef(2, "Stylo"),
    coverUrl = null,
    variantCount = variantCount,
    totalStock = totalStock,
    minPrice = 199_000,
    maxPrice = 249_000,
)

fun sampleVariant(
    id: Long = 10,
    productId: Long = 5,
    stock: Int = 4,
) = AdminVariant(
    id = id,
    productId = productId,
    sku = "AT-DEN-M",
    size = "M",
    color = "Đen",
    price = 199_000,
    stock = stock,
)

fun sampleImage(id: Long = 1, position: Int = 0) = AdminProductImage(
    id = id,
    url = "/uploads/products/$id.jpg",
    position = position,
)

fun sampleDetail(
    id: Long = 5,
    name: String = "Áo thun cotton",
    status: CatalogStatus = CatalogStatus.ACTIVE,
    categoryId: Long = 1,
    brandId: Long = 2,
    variants: List<AdminVariant> = listOf(sampleVariant()),
    images: List<AdminProductImage> = listOf(sampleImage()),
) = AdminProductDetail(
    id = id,
    name = name,
    description = "Cotton 100%",
    status = status,
    category = AdminRef(categoryId, "Áo"),
    brand = AdminRef(brandId, "Stylo"),
    images = images,
    variants = variants,
    minPrice = 199_000,
    maxPrice = 199_000,
)

fun sampleTaxonomy(
    id: Long = 1,
    name: String = "Áo",
    status: CatalogStatus = CatalogStatus.ACTIVE,
    productCount: Int = 0,
) = AdminTaxonomy(id = id, name = name, status = status, productCount = productCount)

val skuTaken = ApiError(
    kind = ApiErrorKind.HTTP,
    code = "SKU_TAKEN",
    httpStatus = 409,
)

val nameTaken = ApiError(
    kind = ApiErrorKind.HTTP,
    code = "NAME_TAKEN",
    httpStatus = 409,
)

val inUse = ApiError(
    kind = ApiErrorKind.HTTP,
    code = "IN_USE",
    httpStatus = 409,
)
