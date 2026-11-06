package com.stylo.shop.feature.admin

import app.cash.turbine.test
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.ui.taxonomy.AdminTaxonomyViewModel
import com.stylo.shop.feature.admin.ui.taxonomy.TaxonomyStatusFilter
import com.stylo.shop.feature.admin.ui.taxonomy.TaxonomyTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminTaxonomyViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadLoadsBothTabs() = runTest {
        val viewModel = AdminTaxonomyViewModel(FakeAdminCatalogRepository())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.categories.itemsState is UiState.Content)
        assertTrue(state.brands.itemsState is UiState.Content)
        assertEquals(1, (state.categories.itemsState as UiState.Content).data.size)
        assertEquals(1, (state.brands.itemsState as UiState.Content).data.size)
    }

    @Test
    fun statusFilterAppliesToActiveTabOnly() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminTaxonomyViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTab(TaxonomyTab.BRANDS)
        viewModel.onStatusFilterSelected(TaxonomyStatusFilter.INACTIVE)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CatalogStatus.INACTIVE, repository.brandQueries.last().status)
        assertTrue(repository.categoryQueries.all { it.status == null })
    }

    @Test
    fun tabSwitchKeepsPerTabSearch() = runTest {
        val viewModel = AdminTaxonomyViewModel(FakeAdminCatalogRepository())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("áo")
        viewModel.selectTab(TaxonomyTab.BRANDS)

        assertEquals("", viewModel.uiState.value.currentList.searchQuery)

        viewModel.selectTab(TaxonomyTab.CATEGORIES)

        assertEquals("áo", viewModel.uiState.value.currentList.searchQuery)
    }

    @Test
    fun duplicateNameKeepsEditorOpen() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            createCategoryResult = { ApiResult.Failure(nameTaken) }
        }
        val viewModel = AdminTaxonomyViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.openCreate()
        viewModel.onEditorNameChange("Đã tồn tại")
        viewModel.saveEditor()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor)
        assertEquals(AdminFieldError.NAME_TAKEN, editor?.nameError)
        assertEquals(false, editor?.saving)
    }

    @Test
    fun deleteInUseExplainsWhyInsteadOfDeleting() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            deleteCategoryResult = ApiResult.Failure(inUse)
        }
        val viewModel = AdminTaxonomyViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val target = sampleTaxonomy(id = 3, productCount = 4)

        viewModel.requestDelete(target)

        viewModel.effects.test {
            viewModel.confirmDelete()
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                UiEffect.ShowMessageRes(
                    R.string.admin_taxonomy_delete_in_use,
                    listOf(4),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.uiState.value.confirmDelete)
    }

    @Test
    fun disableShortcutOpensEditorWithInactiveStatus() = runTest {
        val viewModel = AdminTaxonomyViewModel(FakeAdminCatalogRepository())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.openDisable(sampleTaxonomy(id = 3, status = CatalogStatus.ACTIVE))

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor)
        assertEquals(CatalogStatus.INACTIVE, editor?.status)
    }
}
