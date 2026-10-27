package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.andrewdev.webwallet.core.database.CategoryDao
import org.andrewdev.webwallet.core.database.PayeeDao
import org.andrewdev.webwallet.core.database.TagDao
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryDraft
import org.andrewdev.webwallet.core.model.CategoryNature
import org.andrewdev.webwallet.core.model.CategoryOrder
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.CategoriesApi
import org.andrewdev.webwallet.core.network.generated.api.PayeesApi
import org.andrewdev.webwallet.core.network.generated.api.TagsApi
import org.andrewdev.webwallet.core.network.generated.model.CategoryOrderRequest
import org.andrewdev.webwallet.core.network.generated.model.CategoryRequest
import org.andrewdev.webwallet.core.network.generated.model.CategoryResponse
import org.andrewdev.webwallet.core.network.generated.model.PayeeRequest
import org.andrewdev.webwallet.core.network.generated.model.TagRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Categories, tags and payees: small lists refreshed every six hours and after edits (docs/02 §5.1). */
@Singleton
class CatalogRepository @Inject constructor(
    private val categoriesApi: CategoriesApi,
    private val tagsApi: TagsApi,
    private val payeesApi: PayeesApi,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val payeeDao: PayeeDao,
    private val cache: Cache,
) {
    private val fetchedCategories = MutableStateFlow(false)

    private val fetchedTags = MutableStateFlow(false)

    private val fetchedPayees = MutableStateFlow(false)

    val categories: Flow<CacheState<List<Category>>> = cache.observe(
        key = KEY_CATEGORIES,
        ttl = Cache.LONG,
        local = combine(categoryDao.observeAll(), fetchedCategories) { rows, done ->
            rows.toTree().takeIf {
                rows.isNotEmpty() || done
            }
        },
        fetch = {
            apiCall { categoriesApi.tree() }.map { tree ->
                categoryDao.replaceAll(tree.flatMap { it.flattenToEntities() })
                fetchedCategories.value = true
            }
        },
    )

    val tags: Flow<CacheState<List<Tag>>> = cache.observe(
        key = KEY_TAGS,
        ttl = Cache.LONG,
        local = combine(tagDao.observeAll(), fetchedTags) { rows, done ->
            rows.map {
                it.toModel()
            }.takeIf { rows.isNotEmpty() || done }
        },
        fetch = {
            apiCall { tagsApi.list1() }.map { list ->
                tagDao.replaceAll(list.map { it.toEntity() })
                fetchedTags.value = true
            }
        },
    )

    val payees: Flow<CacheState<List<Payee>>> = cache.observe(
        key = KEY_PAYEES,
        ttl = Cache.LONG,
        local = combine(payeeDao.observeAll(), fetchedPayees) { rows, done ->
            rows.map {
                it.toModel()
            }.takeIf { rows.isNotEmpty() || done }
        },
        fetch = {
            apiCall { payeesApi.list3() }.map { list ->
                payeeDao.replaceAll(list.map { it.toPayeeEntity() })
                fetchedPayees.value = true
            }
        },
    )

    fun invalidate() = cache.invalidate(KEY_CATEGORIES, KEY_TAGS, KEY_PAYEES)

    /** A new tag typed into the chip input; the editor then references it by id. */
    suspend fun createTag(name: String, color: String? = null, key: UUID? = null): ApiResult<Tag> =
        apiCall { tagsApi.create1(TagRequest(name = name.trim(), color = tagColor(color)), key) }
            .map { it.toEntity().toModel() }
            .onOk { cache.invalidate(KEY_TAGS) }

    suspend fun updateTag(id: Long, name: String, color: String?): ApiResult<Tag> =
        apiCall { tagsApi.rename(id, TagRequest(name = name.trim(), color = tagColor(color))) }
            .map { it.toEntity().toModel() }
            .onOk { cache.invalidate(KEY_TAGS) }

    /** The tag leaves every transaction, so the feed is reloaded too. */
    suspend fun deleteTag(id: Long): ApiResult<Unit> = apiCall { tagsApi.delete1(id) }.onOk {
        cache.invalidate(KEY_TAGS)
        cache.invalidatePrefix(TransactionsRepository.FEED_PREFIX)
    }

    suspend fun renamePayee(id: Long, name: String): ApiResult<Payee> =
        apiCall { payeesApi.rename1(id, PayeeRequest(name.trim())) }.map { it.toPayeeEntity().toModel() }
            .onOk {
                cache.invalidate(KEY_PAYEES)
                cache.invalidatePrefix(TransactionsRepository.FEED_PREFIX)
            }

    /** The transactions stay without a payee, the rules for it go. */
    suspend fun deletePayee(id: Long): ApiResult<Unit> = apiCall { payeesApi.delete3(id) }.onOk {
        cache.invalidate(KEY_PAYEES)
        cache.invalidatePrefix(TransactionsRepository.FEED_PREFIX)
    }

    suspend fun createCategory(draft: CategoryDraft, key: UUID): ApiResult<Category> =
        apiCall { categoriesApi.create7(draft.toRequest(), key) }.map { it.toModel() }
            .onOk { cache.invalidate(KEY_CATEGORIES) }

    suspend fun updateCategory(id: Long, draft: CategoryDraft): ApiResult<Category> =
        apiCall { categoriesApi.update6(id, draft.toRequest()) }.map { it.toModel() }
            .onOk { cache.invalidate(KEY_CATEGORIES) }

    /** Only a category without transactions, budget amounts or subcategories; the others are archived instead. */
    suspend fun deleteCategory(id: Long): ApiResult<Unit> = apiCall { categoriesApi.delete7(id) }.onOk {
        cache.invalidate(KEY_CATEGORIES)
    }

    /** The whole order of a kind after a drag: groups and their subcategories, all applied or none. */
    suspend fun reorderCategories(order: List<CategoryOrder>): ApiResult<Unit> = apiCall {
        categoriesApi.reorder(order.map { CategoryOrderRequest(it.id, it.sortOrder, it.parentId) })
    }.map { tree ->
        categoryDao.replaceAll(tree.flatMap { it.flattenToEntities() })
        cache.markFresh(KEY_CATEGORIES)
    }

    /** The response as a model; a subcategory comes without its parent, so it is rooted for the tree builder. */
    private fun CategoryResponse.toModel(): Category {
        val rows = flattenToEntities().map { if (it.id == id) it.copy(parentId = null) else it }
        return rows.toTree().first().copy(parentId = parentId)
    }

    private fun tagColor(color: String?) = color?.let { c -> TagRequest.Color.entries.firstOrNull { it.value == c } }

    private fun CategoryDraft.toRequest() = CategoryRequest(
        kind = CategoryRequest.Kind.valueOf(kind.name),
        name = name.trim(),
        archived = archived,
        carryOver = carryOver,
        color = color?.let { c -> CategoryRequest.Color.entries.firstOrNull { it.value == c } },
        icon = icon,
        necessity = nature.takeIf { it != CategoryNature.INHERIT }?.let { CategoryRequest.Necessity.valueOf(it.name) },
        parentId = parentId,
    )

    fun invalidatePayees() = cache.invalidate(KEY_PAYEES)

    companion object {
        const val KEY_CATEGORIES = "categories"
        const val KEY_TAGS = "tags"
        const val KEY_PAYEES = "payees"
    }
}
