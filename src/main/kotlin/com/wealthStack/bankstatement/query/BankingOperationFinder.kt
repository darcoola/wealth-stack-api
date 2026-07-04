package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.Category
import com.wealthStack.bankstatement.CategoryGroup
import jakarta.persistence.criteria.JoinType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

open class BankingOperationFinder(
    private val repository: BankingOperationRepository
) {

    /**
     * Column filters (all optional, combined with AND):
     * - [accounts] / [unmappedAccount] — keep operations whose raw [BankingOperation.account] is in
     *   the set, OR (when [unmappedAccount]) that have no account mapping (null `accountDisplayName`).
     *   The two are OR-ed together so the "(No account)" filter option can sit alongside real accounts.
     * - [groupIds] — keep only operations whose category belongs to one of the given category groups.
     * - [dateFrom]/[dateTo] — inclusive date span bounds (either side may be open).
     */
    open fun findAll(
        globalFilter: String?,
        needsVerificationOnly: Boolean,
        uncategorizedOnly: Boolean,
        accounts: List<String>?,
        unmappedAccount: Boolean,
        groupIds: List<Long>?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        pageable: Pageable
    ): Page<OperationDto> {
        val spec = Specification<BankingOperation> { root, query, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            if (needsVerificationOnly) {
                predicates.add(cb.isTrue(root.get<Boolean>("needsVerification")))
            }

            if (uncategorizedOnly) {
                predicates.add(cb.isNull(root.get<Any>("category")))
            }

            if (!globalFilter.isNullOrBlank()) {
                val likeFilter = "%${globalFilter.lowercase()}%"
                val desc = cb.like(cb.lower(root.get("description")), likeFilter)
                val info = cb.like(cb.lower(root.get("additionalInfo")), likeFilter)
                val accName = cb.like(cb.lower(root.get("accountDisplayName")), likeFilter)
                val acc = cb.like(cb.lower(root.get("account")), likeFilter)

                val catJoin = root.join<BankingOperation, Category>("category", JoinType.LEFT)
                val cat = cb.like(cb.lower(catJoin.get("name")), likeFilter)

                predicates.add(cb.or(desc, info, accName, acc, cat))
            }

            if (!accounts.isNullOrEmpty() || unmappedAccount) {
                val ors = mutableListOf<jakarta.persistence.criteria.Predicate>()
                if (!accounts.isNullOrEmpty()) ors.add(root.get<String>("account").`in`(accounts))
                if (unmappedAccount) ors.add(cb.isNull(root.get<Any>("accountDisplayName")))
                predicates.add(cb.or(*ors.toTypedArray()))
            }

            if (!groupIds.isNullOrEmpty()) {
                val catJoin = root.join<BankingOperation, Category>("category", JoinType.INNER)
                val groupJoin = catJoin.join<Category, CategoryGroup>("group", JoinType.INNER)
                predicates.add(groupJoin.get<Long>("id").`in`(groupIds))
            }

            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), dateFrom))
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), dateTo))
            }

            cb.and(*predicates.toTypedArray())
        }

        return repository.findAll(spec, pageable).map { it.toDto() }
    }
}

internal fun BankingOperation.toDto() = OperationDto(
    id = id!!,
    date = date,
    description = description,
    account = account,
    accountDisplayName = accountDisplayName ?: account,
    amount = amount,
    additionalInfo = additionalInfo,
    categoryId = category?.id,
    category = category?.name,
    needsVerification = needsVerification
)
