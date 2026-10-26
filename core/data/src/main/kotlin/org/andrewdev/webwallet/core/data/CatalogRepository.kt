package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.andrewdev.webwallet.core.database.CategoryDao
import org.andrewdev.webwallet.core.database.PayeeDao
import org.andrewdev.webwallet.core.database.TagDao
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.CategoriesApi
import org.andrewdev.webwallet.core.network.generated.api.PayeesApi
import org.andrewdev.webwallet.core.network.generated.api.TagsApi
import org.andrewdev.webwallet.core.network.generated.model.TagRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
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
    suspend fun createTag(name: String): ApiResult<Tag> =
        apiCall { tagsApi.create1(TagRequest(name = name.trim())) }.map { it.toEntity().toModel() }
            .onOk { cache.invalidate(KEY_TAGS) }

    fun invalidatePayees() = cache.invalidate(KEY_PAYEES)

    companion object {
        const val KEY_CATEGORIES = "categories"
        const val KEY_TAGS = "tags"
        const val KEY_PAYEES = "payees"
    }
}
