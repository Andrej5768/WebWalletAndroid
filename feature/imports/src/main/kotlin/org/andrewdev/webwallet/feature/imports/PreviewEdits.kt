package org.andrewdev.webwallet.feature.imports

import org.andrewdev.webwallet.core.model.ImportOperation
import org.andrewdev.webwallet.core.model.OperationEditRequest
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.TransferSuggestion

/** The other half of a transfer: an operation of the same import or a transaction of the ledger. */
data class TransferHalf(val operationKey: String?, val transactionId: Long?) {
    companion object {
        fun of(suggestion: TransferSuggestion) = TransferHalf(suggestion.operationKey, suggestion.transactionId)
    }
}

/** What the user may have changed in one previewed operation; a port of the web's `RowEdit`. */
data class RowEdit(
    val include: Boolean,
    val type: TransactionType,
    val transferAccountId: Long?,
    val categoryId: Long?,
    /** False keeps the suggested category, also one the import would create. */
    val categoryChanged: Boolean,
    val tags: List<String>,
    val comment: String?,
    val rememberRule: Boolean,
    /** Merge this expense or income with the other half into one transfer. */
    val pair: TransferHalf?,
    val touched: Boolean,
)

/** The row logic of the preview (web `src/imports/edits.ts`), kept free of Android for the tests. */
object PreviewEdits {

    fun initialEdit(operation: ImportOperation, tagNames: Map<Long, String>, pair: TransferHalf? = null) = RowEdit(
        include = true,
        type = operation.type,
        transferAccountId = null,
        categoryId = operation.categoryId,
        categoryChanged = false,
        tags = operation.tagIds.mapNotNull { tagNames[it] },
        comment = operation.comment,
        rememberRule = false,
        pair = pair,
        touched = false,
    )

    /** The only exact match of an operation, if it has exactly one. */
    private fun exactMatch(operation: ImportOperation): TransferSuggestion? =
        operation.transferSuggestions.filter { it.exact }.singleOrNull()

    private fun TransferHalf.id(): String =
        if (operationKey != null) "operation:$operationKey" else "transaction:$transactionId"

    /**
     * Pairs chosen in advance: an operation with exactly one exact match, when nothing else claims that match.
     * Within the file both halves must point at each other; both rows then carry the pair.
     */
    fun exactPairs(operations: Collection<ImportOperation>): Map<String, TransferHalf> {
        val all = operations.filter { !it.duplicate }
        val byKey = all.associateBy { it.key }
        val claims = mutableMapOf<String, Int>()
        val candidates = all.mapNotNull { operation ->
            val match = exactMatch(operation) ?: return@mapNotNull null
            if (match.operationKey != null) {
                val other = byKey[match.operationKey] ?: return@mapNotNull null
                if (exactMatch(other)?.operationKey != operation.key) return@mapNotNull null
            }
            val half = TransferHalf.of(match)
            claims[half.id()] = (claims[half.id()] ?: 0) + 1
            operation.key to half
        }
        return candidates.filter { (_, half) -> claims[half.id()] == 1 }.toMap()
    }

    /** Whether the row offers to merge with another half: an expense or income kept as detected. */
    fun canPair(operation: ImportOperation, edit: RowEdit): Boolean =
        !operation.duplicate &&
            operation.transferSuggestions.isNotEmpty() &&
            operation.type != TransactionType.TRANSFER &&
            edit.type == operation.type

    /** Allowed type changes: an expense or income can become a transfer, a transfer either of them. */
    fun allowedTypes(type: TransactionType): List<TransactionType> = if (type == TransactionType.TRANSFER) {
        listOf(TransactionType.TRANSFER, TransactionType.EXPENSE, TransactionType.INCOME)
    } else {
        listOf(type, TransactionType.TRANSFER)
    }

    /** An expense or income turned into a transfer needs the other account. */
    fun needsTransferAccount(operation: ImportOperation, edit: RowEdit): Boolean =
        edit.include &&
            edit.type == TransactionType.TRANSFER &&
            operation.type != TransactionType.TRANSFER &&
            edit.transferAccountId == null

    /** The row shows no category and the import would not give it one. */
    fun isUncategorized(operation: ImportOperation, edit: RowEdit): Boolean {
        if (edit.type == TransactionType.TRANSFER || (edit.pair != null && canPair(operation, edit))) return false
        return if (edit.categoryChanged) {
            edit.categoryId == null
        } else {
            operation.categoryId == null && operation.categoryName == null
        }
    }

    /**
     * The confirm request: every changed or paired row with all its values, since a listed row gets exactly what
     * is sent. Duplicates are never imported, so their rows are left out. [tagIds] maps lower-cased names to ids.
     */
    fun buildEdits(
        operations: Collection<ImportOperation>,
        editOf: (ImportOperation) -> RowEdit,
        tagIds: Map<String, Long>,
    ): List<OperationEditRequest> = operations
        .filter { !it.duplicate }
        .map { it to editOf(it) }
        .filter { (_, edit) -> edit.touched || edit.pair != null }
        .map { (operation, edit) ->
            val transfer = edit.type == TransactionType.TRANSFER
            val pair = edit.pair?.takeIf { edit.include && canPair(operation, edit) }
            OperationEditRequest(
                key = operation.key,
                include = edit.include,
                type = edit.type,
                transferAccountId = edit.transferAccountId?.takeIf {
                    transfer && operation.type != TransactionType.TRANSFER
                },
                keepSuggestedCategory = !edit.categoryChanged,
                categoryId = edit.categoryId?.takeIf { edit.categoryChanged && !transfer },
                tagIds = edit.tags.mapNotNull { tagIds[it.trim().lowercase()] },
                comment = edit.comment,
                rememberRule = edit.categoryChanged && edit.rememberRule,
                pairKey = pair?.operationKey,
                pairTransactionId = pair?.transactionId,
            )
        }
}

/**
 * The edits of a whole preview: the operations seen so far (pages load one by one), the rows the user changed and
 * the pairs chosen in advance. Immutable; every change returns a new state.
 */
data class PreviewEditState(
    val known: Map<String, ImportOperation> = emptyMap(),
    val edits: Map<String, RowEdit> = emptyMap(),
    val tagNames: Map<Long, String> = emptyMap(),
) {
    private val autoPairs: Map<String, TransferHalf> by lazy { PreviewEdits.exactPairs(known.values) }

    fun withOperations(operations: List<ImportOperation>): PreviewEditState {
        val added = operations.filter { it.key !in known }
        return if (added.isEmpty()) this else copy(known = known + added.associateBy { it.key })
    }

    fun initial(operation: ImportOperation): RowEdit =
        PreviewEdits.initialEdit(operation, tagNames, autoPairs[operation.key])

    fun editOf(operation: ImportOperation): RowEdit = edits[operation.key] ?: initial(operation)

    /**
     * Applies a change and keeps the pairs consistent: an operation of the file paired with this one points back
     * at it, and a half chosen here is let go by any other row.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun update(operation: ImportOperation, patch: (RowEdit) -> RowEdit): PreviewEditState {
        val next = edits.toMutableMap()
        fun get(key: String): RowEdit? = next[key] ?: known[key]?.let { initial(it) }
        fun set(key: String, change: (RowEdit) -> RowEdit) {
            get(key)?.let { next[key] = change(it).copy(touched = true) }
        }
        fun release(key: String) {
            val partner = get(key)?.pair?.operationKey
            if (partner != null && get(partner)?.pair?.operationKey == key) set(partner) { it.copy(pair = null) }
        }
        val current = get(operation.key) ?: initial(operation)
        val merged = patch(current)
        val pair = merged.pair?.takeIf { merged.include && PreviewEdits.canPair(operation, merged) }
        if (current.pair != pair) {
            release(operation.key)
            if (pair != null) {
                known.keys.forEach { key ->
                    if (key != operation.key && get(key)?.pair == pair) set(key) { it.copy(pair = null) }
                }
                val partner = pair.operationKey
                if (partner != null) {
                    release(partner)
                    known.keys.forEach { key ->
                        if (get(key)?.pair?.operationKey == operation.key && key != partner) {
                            set(key) { it.copy(pair = null) }
                        }
                    }
                    set(partner) { it.copy(pair = TransferHalf(operation.key, null)) }
                }
            }
        }
        next[operation.key] = merged.copy(pair = pair, touched = true)
        return copy(edits = next)
    }

    /** Sets a category and/or adds a tag on the selected rows; transfers keep having no category. */
    fun applyBulk(keys: Set<String>, categoryId: Long?, tag: String?): PreviewEditState {
        val next = edits.toMutableMap()
        val name = tag?.trim().orEmpty()
        keys.forEach { key ->
            val operation = known[key] ?: return@forEach
            val edit = next[key] ?: initial(operation)
            val categorize = categoryId != null && edit.type != TransactionType.TRANSFER
            next[key] = edit.copy(
                categoryId = if (categorize) categoryId else edit.categoryId,
                categoryChanged = edit.categoryChanged || categorize,
                tags = if (name.isNotEmpty() && edit.tags.none { it.equals(name, ignoreCase = true) }) {
                    edit.tags + name
                } else {
                    edit.tags
                },
                touched = true,
            )
        }
        return copy(edits = next)
    }

    val fresh: List<ImportOperation> get() = known.values.filter { !it.duplicate }

    val uncategorizedCount: Int get() = fresh.count { PreviewEdits.isUncategorized(it, editOf(it)) }

    val excludedCount: Int get() = fresh.count { !editOf(it).include }

    val withoutTransferAccount: Int get() = known.values.count { PreviewEdits.needsTransferAccount(it, editOf(it)) }

    /** Pairs offered, counted once per transfer: a pair within the file counts on its expense half. */
    val possiblePairs: Int
        get() = known.values.filter { PreviewEdits.canPair(it, editOf(it)) }.count { operation ->
            operation.type == TransactionType.EXPENSE ||
                operation.transferSuggestions.none { it.operationKey != null }
        }

    val chosenPairs: Int
        get() = known.values.filter { PreviewEdits.canPair(it, editOf(it)) }.count { operation ->
            val edit = editOf(operation)
            edit.include &&
                edit.pair != null &&
                (edit.pair.operationKey == null || operation.type == TransactionType.EXPENSE)
        }

    /** Tag names of the rows that will be sent, unique ignoring case, for creating the missing tags. */
    fun tagNamesToSend(): List<String> = known.values.map { editOf(it) }
        .filter { (it.touched || it.pair != null) && it.include }
        .flatMap { it.tags }
        .associateBy { it.trim().lowercase() }
        .values.toList()

    fun request(tagIds: Map<String, Long>): List<OperationEditRequest> =
        PreviewEdits.buildEdits(known.values, ::editOf, tagIds)
}
