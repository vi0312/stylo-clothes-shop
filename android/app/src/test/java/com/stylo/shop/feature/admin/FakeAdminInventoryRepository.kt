package com.stylo.shop.feature.admin

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.data.AdminInventoryRepository
import com.stylo.shop.feature.admin.model.AdminReceiptCreator
import com.stylo.shop.feature.admin.model.AdminReceiptDetail
import com.stylo.shop.feature.admin.model.AdminReceiptItem
import com.stylo.shop.feature.admin.model.AdminReceiptQuery
import com.stylo.shop.feature.admin.model.AdminReceiptSummary
import com.stylo.shop.feature.admin.model.AdminVariantOption
import com.stylo.shop.feature.admin.model.CreateReceiptInput
import com.stylo.shop.feature.admin.model.Paginated

fun testVariantOption(
    id: Long = 44,
    productId: Long = 9,
    productName: String = "Áo thun cotton",
    sku: String = "AT-DEN-M",
    size: String = "M",
    color: String = "Đen",
    price: Long = 199_000,
    stock: Int = 7,
): AdminVariantOption = AdminVariantOption(
    id = id,
    productId = productId,
    productName = productName,
    sku = sku,
    size = size,
    color = color,
    price = price,
    stock = stock,
)

fun testReceiptItem(
    id: Long = 501,
    variantId: Long = 44,
    productName: String = "Áo thun cotton",
    sku: String = "AT-DEN-M",
    quantity: Int = 20,
    unitCost: Long = 120_000,
): AdminReceiptItem = AdminReceiptItem(
    id = id,
    variantId = variantId,
    sku = sku,
    productName = productName,
    size = "M",
    color = "Đen",
    quantity = quantity,
    unitCost = unitCost,
    lineTotal = quantity * unitCost,
)

fun testReceiptSummary(
    id: Long = 1,
    code: String = "PN-261107-0001",
    supplier: String = "Xưởng may Bình Tân",
    totalCost: Long = 2_400_000,
    itemCount: Int = 2,
    createdAt: String = "2026-11-07T03:20:00.000Z",
): AdminReceiptSummary = AdminReceiptSummary(
    id = id,
    code = code,
    supplier = supplier,
    note = null,
    totalCost = totalCost,
    itemCount = itemCount,
    createdBy = AdminReceiptCreator(id = 1, fullName = "Quản trị viên"),
    createdAt = createdAt,
)

fun testReceiptDetail(
    id: Long = 1,
    code: String = "PN-261107-0001",
    supplier: String = "Xưởng may Bình Tân",
    note: String? = null,
    items: List<AdminReceiptItem> = listOf(testReceiptItem()),
): AdminReceiptDetail = AdminReceiptDetail(
    id = id,
    code = code,
    supplier = supplier,
    note = note,
    totalCost = items.sumOf { it.lineTotal },
    itemCount = items.size,
    createdBy = AdminReceiptCreator(id = 1, fullName = "Quản trị viên"),
    items = items,
    createdAt = "2026-11-07T03:20:00.000Z",
)

class FakeAdminInventoryRepository(
    pageResults: List<ApiResult<Paginated<AdminReceiptSummary>>> = listOf(
        ApiResult.Success(
            Paginated(items = listOf(testReceiptSummary()), page = 1, limit = 20, total = 1),
        ),
    ),
    var detail: ApiResult<AdminReceiptDetail> = ApiResult.Success(testReceiptDetail()),
    var createResult: ApiResult<AdminReceiptDetail>? = null,
    variantResults: List<ApiResult<List<AdminVariantOption>>> = listOf(
        ApiResult.Success(listOf(testVariantOption())),
    ),
) : AdminInventoryRepository {

    private val pageResults = pageResults.toMutableList()
    private val variantResults = variantResults.toMutableList()
    val queries = mutableListOf<AdminReceiptQuery>()
    val createInputs = mutableListOf<CreateReceiptInput>()
    val variantQueries = mutableListOf<String>()
    var detailRequests = 0

    override suspend fun listReceipts(
        query: AdminReceiptQuery,
    ): ApiResult<Paginated<AdminReceiptSummary>> {
        queries += query
        val result = pageResults.first()
        if (pageResults.size > 1) pageResults.removeAt(0)
        return result
    }

    override suspend fun getReceipt(receiptId: Long): ApiResult<AdminReceiptDetail> {
        detailRequests += 1
        return detail
    }

    override suspend fun createReceipt(input: CreateReceiptInput): ApiResult<AdminReceiptDetail> {
        createInputs += input
        return createResult ?: ApiResult.Success(
            testReceiptDetail(id = 55, supplier = input.supplier, note = input.note),
        )
    }

    override suspend fun searchVariants(
        query: String,
        limit: Int,
    ): ApiResult<List<AdminVariantOption>> {
        variantQueries += query
        val result = variantResults.first()
        if (variantResults.size > 1) variantResults.removeAt(0)
        return result
    }
}
