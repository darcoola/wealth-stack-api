package com.wealthStack.bankstatement

import jakarta.persistence.*

/** Whether a category groups money going out (spending) or money coming in (income). */
enum class CategoryType {
    SPENDING,
    INCOME
}

/**
 * A user-curated classification an operation can be tagged with. Categories form an editable
 * dictionary the user fully owns (create / rename / delete) — they are no longer derived from a
 * bank's own transaction type. Each carries a [type] (spending vs income) that drives reporting:
 * totals are summed as-is (no debit/credit split) and charts split by this type, not the amount
 * sign. A future `parentId` would turn this into a subcategory tree.
 */
@Entity
@Table(name = "categories")
class Category(
    @Column(nullable = false, unique = true)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CategoryType = CategoryType.SPENDING,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
