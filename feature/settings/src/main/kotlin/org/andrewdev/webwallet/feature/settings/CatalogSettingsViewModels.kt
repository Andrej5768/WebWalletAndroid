package org.andrewdev.webwallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.data.CacheState
import org.andrewdev.webwallet.core.data.CatalogRepository
import org.andrewdev.webwallet.core.data.RulesRepository
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryDraft
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.CategoryNature
import org.andrewdev.webwallet.core.model.CategoryOrder
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Rule
import org.andrewdev.webwallet.core.model.RuleDraft
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ApiResult
import java.util.UUID
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

// --- categories

data class CategoriesUiState(
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val groups: List<Category> = emptyList(),
    val loading: Boolean = true,
    val error: ApiError? = null,
    /** Categories whose change is running. */
    val busy: Set<Long> = emptySet(),
)

/** The categories of the settings (docs/04 §14): two kinds, a two-level tree, moved and archived in place. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {

    private val kind = MutableStateFlow(CategoryKind.EXPENSE)
    private val busy = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<CategoriesUiState> = combine(catalog.categories, kind, busy) { state, k, running ->
        CategoriesUiState(
            kind = k,
            groups = state.dataOrNull.orEmpty().filter { it.kind == k },
            loading = state is CacheState.Missing && state.loading,
            error = (state as? CacheState.Missing)?.error,
            busy = running,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState())

    fun setKind(value: CategoryKind) = kind.update { value }

    fun refresh() = catalog.invalidate()

    fun setArchived(category: Category, archived: Boolean, onError: (ApiError) -> Unit) =
        run(category.id, onError) { catalog.updateCategory(category.id, category.toDraft().copy(archived = archived)) }

    fun delete(category: Category, onError: (ApiError) -> Unit) =
        run(category.id, onError) { catalog.deleteCategory(category.id) }

    /** Moves a group among the groups, or a subcategory within or into another group (web `moveCategory`). */
    fun move(activeId: Long, overId: Long, onError: (ApiError) -> Unit) {
        val order = CategoryOrdering.move(uiState.value.groups, activeId, overId) ?: return
        run(activeId, onError) { catalog.reorderCategories(order) }
    }

    private fun run(id: Long, onError: (ApiError) -> Unit, action: suspend () -> ApiResult<*>) {
        if (id in busy.value) return
        busy.update { it + id }
        viewModelScope.launch {
            val result = action()
            busy.update { it - id }
            if (result is ApiResult.Err) onError(result.error)
        }
    }
}

/** The order of a kind after a drag of [activeId] onto [overId], as the web's `categoryOrder.ts` does it. */
object CategoryOrdering {
    @Suppress("ReturnCount")
    fun move(groups: List<Category>, activeId: Long, overId: Long): List<CategoryOrder>? {
        if (activeId == overId) return null
        val flat = groups.flatMap { listOf(it) + it.children }
        val active = flat.firstOrNull { it.id == activeId } ?: return null
        val over = flat.firstOrNull { it.id == overId } ?: return null
        val activeParent = active.parentId
        val overParent = over.parentId
        val groupIds = groups.map { it.id }.toMutableList()
        val children = groups.associate { it.id to it.children.map { c -> c.id }.toMutableList() }.toMutableMap()
        if (activeParent == null) {
            val target = overParent ?: over.id
            val from = groupIds.indexOf(active.id)
            val to = groupIds.indexOf(target)
            if (from == to) return null
            groupIds.removeAt(from)
            groupIds.add(to, active.id)
        } else {
            children.getValue(activeParent).remove(active.id)
            val movingDown = flat.indexOf(over) > flat.indexOf(active)
            if (overParent == null) {
                val index = groupIds.indexOf(over.id)
                val targetGroup = if (movingDown || index == 0) over.id else groupIds[index - 1]
                val list = children.getValue(targetGroup)
                if (movingDown || index == 0) list.add(0, active.id) else list.add(active.id)
            } else {
                val list = children.getValue(overParent)
                val overIndex = list.indexOf(over.id)
                list.add(if (movingDown) overIndex + 1 else overIndex, active.id)
            }
        }
        return groupIds.flatMapIndexed { groupIndex, groupId ->
            listOf(CategoryOrder(groupId, null, groupIndex)) +
                children.getValue(groupId).mapIndexed { index, id -> CategoryOrder(id, groupId, index) }
        }
    }
}

fun Category.toDraft() = CategoryDraft(
    name = name,
    kind = kind,
    parentId = parentId,
    icon = icon,
    color = color,
    nature = nature,
    archived = archived,
    carryOver = carryOver,
)

data class CategoryEditorUiState(
    val loaded: Boolean = false,
    val editing: Category? = null,
    val creationKey: UUID = UUID.randomUUID(),
    val groups: List<Category> = emptyList(),
    val name: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val parentId: Long? = null,
    val icon: String? = null,
    val color: String? = null,
    val nature: CategoryNature = CategoryNature.INHERIT,
    val archived: Boolean = false,
    val carryOver: Boolean = false,
    val saving: Boolean = false,
    val validated: Boolean = false,
    val error: ApiError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isNew: Boolean get() = editing == null

    /** A category with subcategories is a group itself and cannot move under another. */
    val parentLocked: Boolean get() = editing?.children?.isNotEmpty() == true
    val nameMissing: Boolean get() = validated && name.isBlank()
    val parent: Category? get() = groups.firstOrNull { it.id == parentId }
}

@HiltViewModel
class CategoryEditorViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {

    private val state = MutableStateFlow(CategoryEditorUiState())
    val uiState: StateFlow<CategoryEditorUiState> = state.asStateFlow()

    fun load(id: Long?, kind: CategoryKind, parentId: Long?) {
        if (state.value.loaded) return
        viewModelScope.launch {
            val all = catalog.categories.first().dataOrNull.orEmpty()
            val editing = id?.let { all.flatMap { c -> c.flatten() }.firstOrNull { it.id == id } }
            val k = editing?.kind ?: kind
            state.value = CategoryEditorUiState(
                loaded = true,
                editing = editing,
                groups = all.filter { it.kind == k && it.id != id },
                name = editing?.name.orEmpty(),
                kind = k,
                parentId = editing?.parentId ?: parentId,
                icon = editing?.icon,
                color = editing?.color,
                nature = editing?.nature ?: CategoryNature.INHERIT,
                archived = editing?.archived ?: false,
                carryOver = editing?.carryOver ?: false,
            )
        }
    }

    fun onNameChange(value: String) = state.update { it.copy(name = value, fieldErrors = it.fieldErrors - "name") }

    fun onParentChange(id: Long?) = state.update { it.copy(parentId = id) }

    fun onIconChange(icon: String?) = state.update { it.copy(icon = icon) }

    fun onColorChange(color: String?) = state.update { it.copy(color = color) }

    fun onNatureChange(nature: CategoryNature) = state.update { it.copy(nature = nature) }

    fun onArchivedChange(archived: Boolean) = state.update { it.copy(archived = archived) }

    fun onCarryOverChange(carryOver: Boolean) = state.update { it.copy(carryOver = carryOver) }

    fun save(onSaved: () -> Unit) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.nameMissing || current.saving) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = CategoryDraft(
                name = current.name,
                kind = current.kind,
                parentId = if (current.parentLocked) null else current.parentId,
                icon = current.icon,
                color = current.color,
                nature = current.nature,
                archived = current.archived,
                carryOver = current.carryOver,
            )
            val result = current.editing?.let { catalog.updateCategory(it.id, draft) }
                ?: catalog.createCategory(draft, current.creationKey)
            when (result) {
                is ApiResult.Ok -> onSaved()
                is ApiResult.Err -> state.update {
                    it.copy(
                        saving = false,
                        error = result.error,
                        fieldErrors = (result.error as? ApiError.Problem)?.fieldErrors.orEmpty(),
                    )
                }
            }
        }
    }
}

// --- rules

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val loading: Boolean = true,
    val error: ApiError? = null,
    val applying: Boolean = false,
)

/** The categorization rules (docs/04 §14 Rules). */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val rules: RulesRepository,
    catalog: CatalogRepository,
) : ViewModel() {

    private val applying = MutableStateFlow(false)

    val uiState: StateFlow<RulesUiState> = combine(rules.rules, catalog.categories, applying) { state, cats, busy ->
        RulesUiState(
            rules = state.dataOrNull.orEmpty(),
            categoriesById = cats.dataOrNull.orEmpty().flatMap { it.flatten() }.associateBy { it.id },
            loading = state is CacheState.Missing && state.loading,
            error = (state as? CacheState.Missing)?.error,
            applying = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RulesUiState())

    fun refresh() = rules.invalidate()

    fun delete(rule: Rule, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = rules.delete(rule.id)
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    /** Categorizes the transactions without a category; reports how many. */
    fun apply(onResult: (ApiResult<Int>) -> Unit) {
        if (applying.value) return
        applying.value = true
        viewModelScope.launch {
            val result = rules.apply()
            applying.value = false
            onResult(result)
        }
    }
}

enum class RuleConditionType { PAYEE, BANK }

data class RuleEditorUiState(
    val loaded: Boolean = false,
    val editing: Rule? = null,
    val creationKey: UUID = UUID.randomUUID(),
    val categories: List<Category> = emptyList(),
    val type: RuleConditionType = RuleConditionType.PAYEE,
    val value: String = "",
    val categoryId: Long? = null,
    val saving: Boolean = false,
    val validated: Boolean = false,
    val error: ApiError? = null,
) {
    val isNew: Boolean get() = editing == null
    val valueMissing: Boolean get() = validated && value.isBlank()
    val categoryMissing: Boolean get() = validated && categoryId == null
    val category: Category? get() = categories.flatMap { it.flatten() }.firstOrNull { it.id == categoryId }
}

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val rules: RulesRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val state = MutableStateFlow(RuleEditorUiState())
    val uiState: StateFlow<RuleEditorUiState> = state.asStateFlow()

    fun load(id: Long?) {
        if (state.value.loaded) return
        viewModelScope.launch {
            val categories = catalog.categories.first().dataOrNull.orEmpty().filter { !it.archived }
            val editing = id?.let { rules.rules.first().dataOrNull?.firstOrNull { r -> r.id == it } }
            state.value = RuleEditorUiState(
                loaded = true,
                editing = editing,
                categories = categories,
                type = if (editing?.bankCategory != null) RuleConditionType.BANK else RuleConditionType.PAYEE,
                value = editing?.bankCategory ?: editing?.payeePattern ?: editing?.payee?.name.orEmpty(),
                categoryId = editing?.categoryId,
            )
        }
    }

    fun onTypeChange(type: RuleConditionType) = state.update { it.copy(type = type) }

    fun onValueChange(value: String) = state.update { it.copy(value = value) }

    fun onCategoryChange(id: Long?) = state.update { it.copy(categoryId = id) }

    fun save(onSaved: () -> Unit) {
        val current = state.value.copy(validated = true)
        state.value = current
        if (current.valueMissing || current.categoryMissing || current.saving) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = RuleDraft(
                categoryId = current.categoryId!!,
                payeePattern = current.value.takeIf { current.type == RuleConditionType.PAYEE },
                bankCategory = current.value.takeIf { current.type == RuleConditionType.BANK },
            )
            val result = current.editing?.let { rules.update(it.id, draft) } ?: rules.create(draft, current.creationKey)
            when (result) {
                is ApiResult.Ok -> onSaved()
                is ApiResult.Err -> state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }
}

// --- payees and tags

data class PayeesUiState(
    val payees: List<Payee> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val error: ApiError? = null,
) {
    val shown: List<Payee>
        get() = payees.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
}

/** The payees (docs/04 §14 Payees): a searchable list, renamed inline, deleted. */
@HiltViewModel
class PayeesViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<PayeesUiState> = combine(catalog.payees, query) { state, q ->
        PayeesUiState(
            payees = state.dataOrNull.orEmpty(),
            query = q,
            loading = state is CacheState.Missing && state.loading,
            error = (state as? CacheState.Missing)?.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PayeesUiState())

    fun setQuery(value: String) = query.update { value }

    fun refresh() = catalog.invalidatePayees()

    fun rename(payee: Payee, name: String, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = catalog.renamePayee(payee.id, name)
            if (result is ApiResult.Err) onError(result.error)
        }
    }

    fun delete(payee: Payee, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = catalog.deletePayee(payee.id)
            if (result is ApiResult.Err) onError(result.error)
        }
    }
}

data class TagsUiState(val tags: List<Tag> = emptyList(), val loading: Boolean = true, val error: ApiError? = null)

/** The tags (docs/04 §14 Tags): a list with colour dots, an editor sheet for the name and colour. */
@HiltViewModel
class TagsViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {

    val uiState: StateFlow<TagsUiState> = catalog.tags.map { state ->
        TagsUiState(
            tags = state.dataOrNull.orEmpty().sortedBy { it.name.lowercase() },
            loading = state is CacheState.Missing && state.loading,
            error = (state as? CacheState.Missing)?.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TagsUiState())

    fun refresh() = catalog.invalidate()

    fun save(tag: Tag?, name: String, color: String?, key: UUID, onResult: (ApiError?) -> Unit) {
        viewModelScope.launch {
            val result = tag?.let { catalog.updateTag(it.id, name, color) } ?: catalog.createTag(name, color, key)
            onResult((result as? ApiResult.Err)?.error)
        }
    }

    fun delete(tag: Tag, onError: (ApiError) -> Unit) {
        viewModelScope.launch {
            val result = catalog.deleteTag(tag.id)
            if (result is ApiResult.Err) onError(result.error)
        }
    }
}

/** Where the categories screen sends the user: the editor for a new category (of a kind, under a group) or one. */
class CategoriesActions(
    val openEditor: (id: Long?, kind: CategoryKind, parentId: Long?) -> Unit,
    val back: () -> Unit,
)
