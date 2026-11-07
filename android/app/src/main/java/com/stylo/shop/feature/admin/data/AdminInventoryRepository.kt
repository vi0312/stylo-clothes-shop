package com.stylo.shop.feature.admin.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.admin.model.AdminReceiptCreator
import com.stylo.shop.feature.admin.model.AdminReceiptDetail
import com.stylo.shop.feature.admin.model.AdminReceiptItem
import com.stylo.shop.feature.admin.model.AdminReceiptQuery
import com.stylo.shop.feature.admin.model.AdminReceiptSummary
import com.stylo.shop.feature.admin.model.AdminVariantOption
import com.stylo.shop.feature.admin.model.CreateReceiptInput
import com.stylo.shop.feature.admin.model.Paginated
import javax.inject.Inject

interface AdminInventoryRepository {

    suspend fun listReceipts(query: AdminReceiptQuery): ApiResult<Paginated<AdminReceiptSummary>>

    suspend fun getReceipt(receiptId: Long): ApiResult<AdminReceiptDetail>

    suspend fun createReceipt(input: CreateReceiptInput): ApiResult<AdminReceiptDetail>

    suspend fun searchVariants(query: String, limit: Int = 20): ApiResult<List<AdminVariantOption>>
}

class DefaultAdminInventoryRepository @Inject constructor(
    private val api: AdminInventoryApi,
) : AdminInventoryRepository {

    override suspend fun listReceipts(
        query: AdminReceiptQuery,
    ): ApiResult<Paginated<AdminReceiptSummary>> = apiCall {
        api.listReceipts(
            from = query.from,
            to = query.to,
            sort = query.sort,
            page = query.page,
            limit = query.limit,
        ).toPaginated()
    }

    override suspend fun getReceipt(receiptId: Long): ApiResult<AdminReceiptDetail> = apiCall {
        api.getReceipt(receiptId).toDetail()
    }

    override suspend fun createReceipt(input: CreateReceiptInput): ApiResult<AdminReceiptDetail> =
        apiCall {
            api.createReceipt(
                CreateReceiptRequest(
                    supplier = input.supplier,
                    note = input.note,
                    items = input.items.map {
                        CreateReceiptItemRequest(
                            variantId = it.variantId,
                            quantity = it.quantity,
                            unitCost = it.unitCost,
                        )
                    },
                ),
            ).toDetail()
        }

    override suspend fun searchVariants(
        query: String,
        limit: Int,
    ): ApiResult<List<AdminVariantOption>> = apiCall {
        api.searchVariants(q = query.trim().ifEmpty { null }, limit = limit)
            .items
            .map { it.toOption() }
    }
}

private fun AdminReceiptPageDto.toPaginated(): Paginated<AdminReceiptSummary> = Paginated(
    items = items.map { it.toSummary() },
    page = page,
    limit = limit,
    total = total,
)

private fun AdminReceiptSummaryDto.toSummary(): AdminReceiptSummary = AdminReceiptSummary(
    id = id,
    code = code,
    supplier = supplier,
    note = note,
    totalCost = totalCost,
    itemCount = itemCount,
    createdBy = createdBy.toCreator(),
    createdAt = createdAt,
)

private fun AdminReceiptDetailDto.toDetail(): AdminReceiptDetail = AdminReceiptDetail(
    id = id,
    code = code,
    supplier = supplier,
    note = note,
    totalCost = totalCost,
    itemCount = itemCount,
    createdBy = createdBy.toCreator(),
    items = items.map {
        AdminReceiptItem(
            id = it.id,
            variantId = it.variantId,
            sku = it.sku,
            productName = it.productName,
            size = it.size,
            color = it.color,
            quantity = it.quantity,
            unitCost = it.unitCost,
            lineTotal = it.lineTotal,
        )
    },
    createdAt = createdAt,
)

private fun AdminReceiptCreatorDto.toCreator(): AdminReceiptCreator = AdminReceiptCreator(
    id = id,
    fullName = fullName,
)

private fun AdminVariantSearchItemDto.toOption(): AdminVariantOption = AdminVariantOption(
    id = id,
    productId = productId,
    productName = productName,
    sku = sku,
    size = size,
    color = color,
    price = price,
    stock = stock,
)
