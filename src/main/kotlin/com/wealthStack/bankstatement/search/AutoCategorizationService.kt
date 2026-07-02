package com.wealthStack.bankstatement.search

import com.wealthStack.bankstatement.BankingOperation
import com.wealthStack.bankstatement.Category
import com.wealthStack.bankstatement.CategoryRepository
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.search

open class AutoCategorizationService(
    private val searchRepository: BankingOperationSearchRepository,
    private val elasticsearchOperations: ElasticsearchOperations,
    private val categoryRepository: CategoryRepository
) {

    open fun indexOperations(operations: List<BankingOperation>) {
        val documents = operations
            .filter { it.category != null }
            .map {
                BankingOperationDocument(
                    id = "${it.fingerprint}-${it.occurrence}",
                    description = it.description,
                    account = it.account,
                    categoryId = it.category!!.id!!
                )
            }

        if (documents.isNotEmpty()) {
            searchRepository.saveAll(documents)
        }
    }

    open fun predictCategory(operation: BankingOperation): Category? {
        // Simple search on description and account
        val query = NativeQuery.builder()
            .withQuery { q ->
                q.bool { b ->
                    b.should { s ->
                        s.match { m ->
                            m.field("description").query(operation.description)
                        }
                    }
                    b.should { s ->
                        s.match { m ->
                            m.field("account").query(operation.account)
                        }
                    }
                    b.minimumShouldMatch("1")
                }
            }
            .withMaxResults(5)
            .build()

        val searchHits = elasticsearchOperations.search<BankingOperationDocument>(query, IndexCoordinates.of("banking_operations"))

        if (searchHits.isEmpty) {
            return null
        }

        // Extract the unique categories from the top hits
        val distinctCategoryIds = searchHits.searchHits
            .map { it.content.categoryId }
            .distinct()

        // "use category field if single category was find, dont set category if there is nothing found or multiple matches"
        if (distinctCategoryIds.size == 1) {
            val predictedCategoryId = distinctCategoryIds.first()
            return categoryRepository.findById(predictedCategoryId).orElse(null)
        }

        // Multiple conflicting matches found, so we do not auto-categorize
        return null
    }
}
