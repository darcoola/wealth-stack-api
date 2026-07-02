package com.wealthStack.bankstatement.query

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.BankingOperationRepository
import com.wealthStack.bankstatement.Category
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification

open class BankingOperationFinder(
    private val repository: BankingOperationRepository
) {

    open fun findAll(
        globalFilter: String?,
        needsVerificationOnly: Boolean,
        monthDateStr: String?,
        pageable: Pageable
    ): Page<OperationDto> {
        val spec = Specification<BankingOperation> { root, query, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            if (needsVerificationOnly) {
                predicates.add(cb.isTrue(root.get<Boolean>("needsVerification")))
            }

            if (!globalFilter.isNullOrBlank()) {
                val likeFilter = "%${globalFilter.lowercase()}%"
                val desc = cb.like(cb.lower(root.get("description")), likeFilter)
                val info = cb.like(cb.lower(root.get("additionalInfo")), likeFilter)
                val accName = cb.like(cb.lower(root.get("accountDisplayName")), likeFilter)
                val acc = cb.like(cb.lower(root.get("account")), likeFilter)
                
                val catJoin = root.join<BankingOperation, Category>("category", jakarta.persistence.criteria.JoinType.LEFT)
                val cat = cb.like(cb.lower(catJoin.get("name")), likeFilter)
                
                predicates.add(cb.or(desc, info, accName, acc, cat))
            }

            if (!monthDateStr.isNullOrBlank()) {
                val year = monthDateStr.substring(0, 4).toInt()
                val month = monthDateStr.substring(5, 7).toInt()
                val startOfMonth = java.time.LocalDate.of(year, month, 1)
                val endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth())
                predicates.add(cb.between(root.get("date"), startOfMonth, endOfMonth))
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
