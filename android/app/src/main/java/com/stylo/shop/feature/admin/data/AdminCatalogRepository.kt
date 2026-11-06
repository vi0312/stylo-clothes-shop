package com.stylo.shop.feature.admin.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.admin.model.AdminProductDetail
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.AdminProductQuery
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminTaxonomyQuery
import com.stylo.shop.feature.admin.model.AdminVariant
import com.stylo.shop.feature.admin.model.CreateProductInput
import com.stylo.shop.feature.admin.model.CreateTaxonomyInput
import com.stylo.shop.feature.admin.model.CreateVariantInput
import com.stylo.shop.feature.admin.model.ImageUpload
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.UpdateProductInput
import com.stylo.shop.feature.admin.model.UpdateTaxonomyInput
import com.stylo.shop.feature.admin.model.UpdateVariantInput
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

interface AdminCatalogRepository {

    suspend fun listProducts(query: AdminProductQuery): ApiResult<Paginated<AdminProductSummary>>
    suspend fun getProduct(id: Long): ApiResult<AdminProductDetail>
    suspend fun createProduct(input: CreateProductInput): ApiResult<AdminProductDetail>
    suspend fun updateProduct(id: Long, input: UpdateProductInput): ApiResult<AdminProductDetail>
    suspend fun deleteProduct(id: Long): ApiResult<Unit>

    suspend fun createVariant(productId: Long, input: CreateVariantInput): ApiResult<AdminVariant>
    suspend fun updateVariant(id: Long, input: UpdateVariantInput): ApiResult<AdminVariant>
    suspend fun deleteVariant(id: Long): ApiResult<Unit>

    suspend fun uploadImage(productId: Long, image: ImageUpload): ApiResult<AdminProductImage>
    suspend fun deleteImage(productId: Long, imageId: Long): ApiResult<Unit>

    suspend fun listCategories(query: AdminTaxonomyQuery): ApiResult<Paginated<AdminTaxonomy>>
    suspend fun createCategory(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy>
    suspend fun updateCategory(id: Long, input: UpdateTaxonomyInput): ApiResult<AdminTaxonomy>
    suspend fun deleteCategory(id: Long): ApiResult<Unit>

    suspend fun listBrands(query: AdminTaxonomyQuery): ApiResult<Paginated<AdminTaxonomy>>
    suspend fun createBrand(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy>
    suspend fun updateBrand(id: Long, input: UpdateTaxonomyInput): ApiResult<AdminTaxonomy>
    suspend fun deleteBrand(id: Long): ApiResult<Unit>
}

class DefaultAdminCatalogRepository @Inject constructor(
    private val api: AdminCatalogApi,
) : AdminCatalogRepository {

    override suspend fun listProducts(
        query: AdminProductQuery,
    ): ApiResult<Paginated<AdminProductSummary>> = apiCall {
        api.listProducts(
            q = query.q?.takeIf { it.isNotBlank() },
            status = query.status?.apiValue,
            categoryId = query.categoryId,
            brandId = query.brandId,
            sort = query.sort,
            page = query.page,
            limit = query.limit,
        ).toPaginatedProducts()
    }

    override suspend fun getProduct(id: Long): ApiResult<AdminProductDetail> = apiCall {
        api.getProduct(id).toAdminProductDetail()
    }

    override suspend fun createProduct(input: CreateProductInput): ApiResult<AdminProductDetail> =
        apiCall {
            api.createProduct(
                CreateProductRequest(
                    name = input.name,
                    description = input.description,
                    categoryId = input.categoryId,
                    brandId = input.brandId,
                    status = input.status.apiValue,
                ),
            ).toAdminProductDetail()
        }

    override suspend fun updateProduct(
        id: Long,
        input: UpdateProductInput,
    ): ApiResult<AdminProductDetail> = apiCall {
        api.updateProduct(
            id,
            UpdateProductRequest(
                name = input.name,
                description = input.description,
                categoryId = input.categoryId,
                brandId = input.brandId,
                status = input.status?.apiValue,
            ),
        ).toAdminProductDetail()
    }

    override suspend fun deleteProduct(id: Long): ApiResult<Unit> = apiCall {
        api.deleteProduct(id)
    }

    override suspend fun createVariant(
        productId: Long,
        input: CreateVariantInput,
    ): ApiResult<AdminVariant> = apiCall {
        api.createVariant(
            productId,
            CreateVariantRequest(
                sku = input.sku,
                size = input.size,
                color = input.color,
                price = input.price,
            ),
        ).toAdminVariant()
    }

    override suspend fun updateVariant(
        id: Long,
        input: UpdateVariantInput,
    ): ApiResult<AdminVariant> = apiCall {
        api.updateVariant(
            id,
            UpdateVariantRequest(
                sku = input.sku,
                size = input.size,
                color = input.color,
                price = input.price,
            ),
        ).toAdminVariant()
    }

    override suspend fun deleteVariant(id: Long): ApiResult<Unit> = apiCall {
        api.deleteVariant(id)
    }

    override suspend fun uploadImage(
        productId: Long,
        image: ImageUpload,
    ): ApiResult<AdminProductImage> = apiCall {
        val requestBody = image.bytes.toRequestBody(image.mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", image.fileName, requestBody)
        api.uploadImage(productId, part).toAdminImage()
    }

    override suspend fun deleteImage(productId: Long, imageId: Long): ApiResult<Unit> = apiCall {
        api.deleteImage(productId, imageId)
    }

    override suspend fun listCategories(
        query: AdminTaxonomyQuery,
    ): ApiResult<Paginated<AdminTaxonomy>> = apiCall {
        api.listCategories(
            q = query.q?.takeIf { it.isNotBlank() },
            status = query.status?.apiValue,
            sort = query.sort,
            page = query.page,
            limit = query.limit,
        ).toPaginatedTaxonomy()
    }

    override suspend fun createCategory(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy> =
        apiCall {
            api.createCategory(CreateTaxonomyRequest(input.name)).toAdminTaxonomy()
        }

    override suspend fun updateCategory(
        id: Long,
        input: UpdateTaxonomyInput,
    ): ApiResult<AdminTaxonomy> = apiCall {
        api.updateCategory(
            id,
            UpdateTaxonomyRequest(name = input.name, status = input.status?.apiValue),
        ).toAdminTaxonomy()
    }

    override suspend fun deleteCategory(id: Long): ApiResult<Unit> = apiCall {
        api.deleteCategory(id)
    }

    override suspend fun listBrands(
        query: AdminTaxonomyQuery,
    ): ApiResult<Paginated<AdminTaxonomy>> = apiCall {
        api.listBrands(
            q = query.q?.takeIf { it.isNotBlank() },
            status = query.status?.apiValue,
            sort = query.sort,
            page = query.page,
            limit = query.limit,
        ).toPaginatedTaxonomy()
    }

    override suspend fun createBrand(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy> =
        apiCall {
            api.createBrand(CreateTaxonomyRequest(input.name)).toAdminTaxonomy()
        }

    override suspend fun updateBrand(
        id: Long,
        input: UpdateTaxonomyInput,
    ): ApiResult<AdminTaxonomy> = apiCall {
        api.updateBrand(
            id,
            UpdateTaxonomyRequest(name = input.name, status = input.status?.apiValue),
        ).toAdminTaxonomy()
    }

    override suspend fun deleteBrand(id: Long): ApiResult<Unit> = apiCall {
        api.deleteBrand(id)
    }
}
