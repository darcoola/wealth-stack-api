package com.wealthStack.bankstatement

import jakarta.persistence.*

/**
 * How a category groups money: [SPENDING] (going out), [INCOME] (coming in), or [OTHERS] for
 * anything that is neither (transfers, investments, corrections, …).
 */
enum class CategoryType {
    SPENDING,
    INCOME,
    OTHERS
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
