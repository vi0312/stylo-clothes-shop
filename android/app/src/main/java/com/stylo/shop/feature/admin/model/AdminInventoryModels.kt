package com.stylo.shop.feature.admin.model

data class AdminVariantOption(
    val id: Long,
    val productId: Long,
    val productName: String,
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
    val stock: Int,
)

data class AdminReceiptItem(
    val id: Long,
    val variantId: Long,
    val sku: String,
    val productName: String,
    val size: String,
    val color: String,
    val quantity: Int,
    val unitCost: Long,
    val lineTotal: Long,
)

data class AdminReceiptCreator(
    val id: Long,
    val fullName: String,
)

data class AdminReceiptSummary(
    val id: Long,
    val code: String,
    val supplier: String,
    val note: String?,
    val totalCost: Long,
    val itemCount: Int,
    val createdBy: AdminReceiptCreator,
    val createdAt: String,
)

data class AdminReceiptDetail(
    val id: Long,
    val code: String,
    val supplier: String,
    val note: String?,
    val totalCost: Long,
    val itemCount: Int,
    val createdBy: AdminReceiptCreator,
    val items: List<AdminReceiptItem>,
    val createdAt: String,
)

data class AdminReceiptQuery(
    val from: String? = null,
    val to: String? = null,
    val sort: String = "newest",
    val page: Int = 1,
    val limit: Int = RECEIPT_PAGE_SIZE,
)

data class CreateReceiptLineInput(
    val variantId: Long,
    val quantity: Int,
    val unitCost: Long,
)

data class CreateReceiptInput(
    val supplier: String,
    val note: String?,
    val items: List<CreateReceiptLineInput>,
)

const val RECEIPT_PAGE_SIZE = 20
const val RECEIPT_SUPPLIER_MAX = 150
const val RECEIPT_NOTE_MAX = 255
