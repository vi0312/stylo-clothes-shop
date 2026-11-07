package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminVariantOption
import com.stylo.shop.feature.admin.ui.inventory.AdminReceiptCreateViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReceiptCreateViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun addLine(
        viewModel: AdminReceiptCreateViewModel,
        option: AdminVariantOption = testVariantOption(),
    ) {
        viewModel.openVariantPicker()
        advance()
        viewModel.selectVariant(option)
    }

    @Test
    fun `opening the picker searches immediately and selecting adds an empty line`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptCreateViewModel(repository)
        viewModel.openVariantPicker()
        advance()

        assertEquals(listOf(""), repository.variantQueries)
        assertEquals(1, (viewModel.uiState.value.picker!!.results as UiState.Content).data.size)

        val option = testVariantOption()
        viewModel.selectVariant(option)
        advance()

        val state = viewModel.uiState.value
        assertNull(state.picker)
        assertEquals(1, state.lines.size)
        assertEquals(option, state.lines.single().variant)
        assertEquals("", state.lines.single().quantity)
        assertEquals("", state.lines.single().unitCost)
    }

    @Test
    fun `typing in the picker debounces the search`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptCreateViewModel(repository)
        viewModel.openVariantPicker()
        advance()

        viewModel.onPickerQueryChange("AT")
        dispatcherRule.dispatcher.scheduler.advanceTimeBy(299)
        dispatcherRule.dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.variantQueries.size)

        advance()
        assertEquals(listOf("", "AT"), repository.variantQueries)
    }

    @Test
    fun `save requires a supplier and at least one line`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptCreateViewModel(repository)

        viewModel.save()
        advance()

        val state = viewModel.uiState.value
        assertEquals(AdminFieldError.REQUIRED, state.supplierError)
        assertTrue(state.itemsError)
        assertEquals(0, repository.createInputs.size)
    }

    @Test
    fun `line quantity and unit cost are validated inline`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptCreateViewModel(repository)
        addLine(viewModel)
        viewModel.onSupplierChange("Xưởng may Bình Tân")

        viewModel.save()
        advance()

        var line = viewModel.uiState.value.lines.single()
        assertEquals(AdminFieldError.REQUIRED, line.quantityError)
        assertEquals(AdminFieldError.REQUIRED, line.unitCostError)

        viewModel.onQuantityChange(0, "0")
        line = viewModel.uiState.value.lines.single()
        assertNull(line.quantityError)
        viewModel.save()
        advance()
        line = viewModel.uiState.value.lines.single()
        assertEquals(AdminFieldError.INVALID_QUANTITY, line.quantityError)
        assertEquals(0, repository.createInputs.size)
    }

    @Test
    fun `a valid receipt posts every line and reports the created id`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptCreateViewModel(repository)
        addLine(viewModel, testVariantOption(id = 44, sku = "AT-DEN-M"))
        addLine(viewModel, testVariantOption(id = 45, sku = "AT-TRANG-L"))
        viewModel.onSupplierChange("  Xưởng may Bình Tân  ")
        viewModel.onNoteChange("Lô thu đông")
        viewModel.onQuantityChange(0, "20")
        viewModel.onUnitCostChange(0, "120000")
        viewModel.onQuantityChange(1, "10")
        viewModel.onUnitCostChange(1, "125000")

        assertEquals(3_650_000, viewModel.uiState.value.totalCost)

        viewModel.save()
        advance()

        val input = repository.createInputs.single()
        assertEquals("Xưởng may Bình Tân", input.supplier)
        assertEquals("Lô thu đông", input.note)
        assertEquals(2, input.items.size)
        assertEquals(44L, input.items[0].variantId)
        assertEquals(20, input.items[0].quantity)
        assertEquals(120_000L, input.items[0].unitCost)
        assertEquals(45L, input.items[1].variantId)
        assertEquals(55L, viewModel.uiState.value.createdReceiptId)
        assertEquals(false, viewModel.uiState.value.saving)
    }

    @Test
    fun `blank note is sent as null and a server failure keeps the form`() = runTest {
        val repository = FakeAdminInventoryRepository(
            createResult = ApiResult.Failure(
                ApiError(kind = ApiErrorKind.HTTP, httpStatus = 500),
            ),
        )
        val viewModel = AdminReceiptCreateViewModel(repository)
        addLine(viewModel)
        viewModel.onSupplierChange("Xưởng may Bình Tân")
        viewModel.onQuantityChange(0, "5")
        viewModel.onUnitCostChange(0, "0")

        viewModel.save()
        advance()

        assertNull(repository.createInputs.single().note)
        assertTrue(viewModel.uiState.value.formError != null)
        assertNull(viewModel.uiState.value.createdReceiptId)
        assertEquals(false, viewModel.uiState.value.saving)
    }
}
