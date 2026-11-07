package com.stylo.shop.feature.admin.ui.taxonomy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminCatalogRepository
import com.stylo.shop.feature.admin.model.ADMIN_TAXONOMY_NAME_MAX
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminTaxonomyQuery
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.CreateTaxonomyInput
import com.stylo.shop.feature.admin.model.UpdateTaxonomyInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaxonomyTab {
    CATEGORIES,
    BRANDS,
}

enum class TaxonomyStatusFilter {
    ALL,
    ACTIVE,
    INACTIVE,
}

private const val TAXONOMY_PAGE_SIZE = 20

data class TaxonomyListUiState(
    val itemsState: UiState<List<AdminTaxonomy>> = UiState.Loading,
    val searchQuery: String = "",
    val statusFilter: TaxonomyStatusFilter = TaxonomyStatusFilter.ALL,
    val page: Int = 1,
    val limit: Int = TAXONOMY_PAGE_SIZE,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
) {
    val hasMore: Boolean
        get() = limit > 0 && page * limit < total
}

data class TaxonomyEditorState(
    val id: Long? = null,
    val name: String = "",
    val status: CatalogStatus = CatalogStatus.ACTIVE,
    val nameError: AdminFieldError? = null,
    val formError: ApiError? = null,
    val saving: Boolean = false,
)

data class AdminTaxonomyUiState(
    val tab: TaxonomyTab = TaxonomyTab.CATEGORIES,
    val categories: TaxonomyListUiState = TaxonomyListUiState(),
    val brands: TaxonomyListUiState = TaxonomyListUiState(),
    val editor: TaxonomyEditorState? = null,
    val confirmDelete: AdminTaxonomy? = null,
    val isDeleting: Boolean = false,
) {
    val currentList: TaxonomyListUiState
        get() = if (tab == TaxonomyTab.CATEGORIES) categories else brands
}

@HiltViewModel
class AdminTaxonomyViewModel @Inject constructor(
    private val repository: AdminCatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminTaxonomyUiState())
    val uiState: StateFlow<AdminTaxonomyUiState> = _uiState.asStateFlow()

    private val loadGenerations = mutableMapOf<TaxonomyTab, Int>()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    init {
        load(TaxonomyTab.CATEGORIES, page = 1)
        load(TaxonomyTab.BRANDS, page = 1)
    }

    fun selectTab(tab: TaxonomyTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onSearchQueryChanged(query: String) {
        updateCurrent { it.copy(searchQuery = query) }
    }

    fun onSearchSubmit() {
        load(_uiState.value.tab, page = 1)
    }

    fun onClearSearch() {
        updateCurrent { it.copy(searchQuery = "") }
        load(_uiState.value.tab, page = 1)
    }

    fun onStatusFilterSelected(filter: TaxonomyStatusFilter) {
        if (_uiState.value.currentList.statusFilter == filter) return
        updateCurrent { it.copy(statusFilter = filter) }
        load(_uiState.value.tab, page = 1)
    }

    fun onLoadMore() {
        val state = _uiState.value
        val list = state.currentList
        if (list.itemsState is UiState.Content && list.hasMore && !list.isLoadingMore) {
            load(state.tab, page = list.page + 1)
        }
    }

    fun onRetry() {
        load(_uiState.value.tab, page = 1)
    }

    fun openCreate() {
        _uiState.update { it.copy(editor = TaxonomyEditorState()) }
    }

    fun openEdit(item: AdminTaxonomy) {
        _uiState.update {
            it.copy(
                editor = TaxonomyEditorState(
                    id = item.id,
                    name = item.name,
                    status = item.status,
                ),
            )
        }
    }

    fun openDisable(item: AdminTaxonomy) {
        _uiState.update {
            it.copy(
                editor = TaxonomyEditorState(
                    id = item.id,
                    name = item.name,
                    status = CatalogStatus.INACTIVE,
                ),
            )
        }
    }

    fun onEditorNameChange(value: String) {
        _uiState.update { current ->
            current.copy(
                editor = current.editor?.copy(
                    name = value,
                    nameError = null,
                    formError = null,
                ),
            )
        }
    }

    fun onEditorStatusChange(status: CatalogStatus) {
        _uiState.update { current ->
            current.copy(editor = current.editor?.copy(status = status))
        }
    }

    fun dismissEditor() {
        if (_uiState.value.editor?.saving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val state = _uiState.value
        val editor = state.editor ?: return
        if (editor.saving) return

        val name = editor.name.trim()
        val nameError = when {
            name.isEmpty() -> AdminFieldError.REQUIRED
            name.length > ADMIN_TAXONOMY_NAME_MAX -> AdminFieldError.TOO_LONG
            else -> null
        }
        if (nameError != null) {
            _uiState.update {
                it.copy(editor = editor.copy(nameError = nameError))
            }
            return
        }

        _uiState.update {
            it.copy(editor = editor.copy(saving = true, nameError = null, formError = null))
        }

        viewModelScope.launch {
            val result = if (editor.id == null) {
                if (state.tab == TaxonomyTab.CATEGORIES) {
                    repository.createCategory(CreateTaxonomyInput(name))
                } else {
                    repository.createBrand(CreateTaxonomyInput(name))
                }
            } else {
                val input = UpdateTaxonomyInput(name = name, status = editor.status)
                if (state.tab == TaxonomyTab.CATEGORIES) {
                    repository.updateCategory(editor.id, input)
                } else {
                    repository.updateBrand(editor.id, input)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(editor = null) }
                    emit(
                        UiEffect.ShowMessageRes(
                            if (editor.id == null) {
                                R.string.admin_taxonomy_created
                            } else {
                                R.string.admin_taxonomy_saved
                            },
                        ),
                    )
                    load(state.tab, page = 1)
                }
                is ApiResult.Failure -> {
                    _uiState.update { current ->
                        val nextEditor = if (result.error.code == "NAME_TAKEN") {
                            current.editor?.copy(nameError = AdminFieldError.NAME_TAKEN, saving = false)
                        } else {
                            current.editor?.copy(formError = result.error, saving = false)
                        }
                        current.copy(editor = nextEditor)
                    }
                }
            }
        }
    }

    fun requestDelete(item: AdminTaxonomy) {
        _uiState.update { it.copy(confirmDelete = item) }
    }

    fun dismissDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val target = state.confirmDelete ?: return
        if (state.isDeleting) return

        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val result = if (state.tab == TaxonomyTab.CATEGORIES) {
                repository.deleteCategory(target.id)
            } else {
                repository.deleteBrand(target.id)
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(confirmDelete = null, isDeleting = false) }
                    emit(UiEffect.ShowMessageRes(R.string.admin_taxonomy_deleted))
                    load(state.tab, page = 1)
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(confirmDelete = null, isDeleting = false) }
                    if (result.error.code == "IN_USE") {
                        emit(
                            UiEffect.ShowMessageRes(
                                R.string.admin_taxonomy_delete_in_use,
                                listOf(target.productCount),
                            ),
                        )
                    } else {
                        emit(UiEffect.ShowError(result.error))
                    }
                }
            }
        }
    }

    private fun load(tab: TaxonomyTab, page: Int) {
        val list = if (tab == TaxonomyTab.CATEGORIES) {
            _uiState.value.categories
        } else {
            _uiState.value.brands
        }
        if (page > 1 && (list.isLoadingMore || !list.hasMore)) return

        // One counter per tab: the two tabs load in parallel and must not cancel each other, but a
        // slower earlier response for the same tab must not overwrite a newer filter.
        val generation = (loadGenerations[tab] ?: 0) + 1
        loadGenerations[tab] = generation
        if (page > 1) {
            updateTab(tab) { it.copy(isLoadingMore = true, loadMoreError = null) }
        }
        viewModelScope.launch {
            val isInitial = page == 1
            updateTab(tab) {
                if (isInitial) {
                    it.copy(itemsState = UiState.Loading, page = 1, isLoadingMore = false, loadMoreError = null)
                } else {
                    it.copy(isLoadingMore = true, loadMoreError = null)
                }
            }

            val snapshot = if (tab == TaxonomyTab.CATEGORIES) {
                _uiState.value.categories
            } else {
                _uiState.value.brands
            }
            val query = AdminTaxonomyQuery(
                q = snapshot.searchQuery.trim().takeIf { it.isNotEmpty() },
                status = when (snapshot.statusFilter) {
                    TaxonomyStatusFilter.ACTIVE -> CatalogStatus.ACTIVE
                    TaxonomyStatusFilter.INACTIVE -> CatalogStatus.INACTIVE
                    TaxonomyStatusFilter.ALL -> null
                },
                page = page,
            )

            val result = if (tab == TaxonomyTab.CATEGORIES) {
                repository.listCategories(query)
            } else {
                repository.listBrands(query)
            }
            if (generation != loadGenerations[tab]) return@launch

            when (result) {
                is ApiResult.Success -> {
                    updateTab(tab) { previous ->
                        val combined = if (isInitial) {
                            result.data.items
                        } else {
                            (previous.itemsState as? UiState.Content)?.data.orEmpty() + result.data.items
                        }
                        previous.copy(
                            itemsState = UiState.Content(combined),
                            page = result.data.page,
                            total = result.data.total,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    updateTab(tab) { previous ->
                        if (isInitial) {
                            previous.copy(
                                itemsState = UiState.Error(result.error),
                                isLoadingMore = false,
                            )
                        } else {
                            previous.copy(
                                isLoadingMore = false,
                                loadMoreError = result.error,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateCurrent(
        transform: (TaxonomyListUiState) -> TaxonomyListUiState,
    ) {
        _uiState.update { state ->
            when (state.tab) {
                TaxonomyTab.CATEGORIES -> state.copy(categories = transform(state.categories))
                TaxonomyTab.BRANDS -> state.copy(brands = transform(state.brands))
            }
        }
    }

    private fun updateTab(
        tab: TaxonomyTab,
        transform: (TaxonomyListUiState) -> TaxonomyListUiState,
    ) {
        _uiState.update { state ->
            when (tab) {
                TaxonomyTab.CATEGORIES -> state.copy(categories = transform(state.categories))
                TaxonomyTab.BRANDS -> state.copy(brands = transform(state.brands))
            }
        }
    }

    private fun emit(effect: UiEffect) {
        _effects.trySend(effect)
    }

}
