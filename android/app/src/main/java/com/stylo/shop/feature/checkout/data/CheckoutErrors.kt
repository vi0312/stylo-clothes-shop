package com.stylo.shop.feature.checkout.data

import com.stylo.shop.core.network.ApiError
import com.stylo.shop.feature.checkout.model.StockShortfall
import com.stylo.shop.feature.checkout.model.SubmitError
import com.stylo.shop.feature.checkout.model.UnavailableLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Turns a rejected checkout into the line-level message the screen shows. Details are
 * best effort: an unknown payload falls back to the generic HTTP message.
 */
internal fun ApiError.toSubmitError(): SubmitError = when (code) {
    "CART_EMPTY" -> SubmitError.CartEmpty
    "OUT_OF_STOCK" -> details
        ?.decodeList<StockShortfallDto>()
        ?.takeIf { it.isNotEmpty() }
        ?.let { lines ->
            SubmitError.Stock(lines.map { StockShortfall(it.sku, it.requested, it.available) })
        }
        ?: SubmitError.Other(this)
    "ITEM_UNAVAILABLE" -> details
        ?.decodeList<UnavailableLineDto>()
        ?.takeIf { it.isNotEmpty() }
        ?.let { lines ->
            SubmitError.Unavailable(lines.map { UnavailableLine(it.sku, it.reason) })
        }
        ?: SubmitError.Other(this)
    else -> SubmitError.Other(this)
}

private val detailsJson = Json { ignoreUnknownKeys = true }

private inline fun <reified T> JsonElement.decodeList(): List<T>? =
    runCatching { detailsJson.decodeFromJsonElement<List<T>>(this) }.getOrNull()

@Serializable
private data class StockShortfallDto(
    val sku: String,
    val requested: Int,
    val available: Int,
)

@Serializable
private data class UnavailableLineDto(
    val sku: String,
    val reason: String,
)
