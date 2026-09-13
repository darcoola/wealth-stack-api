package com.wealthStack.bankstatement

import jakarta.persistence.*

/**
 * A user-curated classification an operation can be tagged with. Categories form an editable
 * dictionary the user fully owns (create / rename / delete) — they are no longer derived from a
 * bank's own transaction type. Each may belong to a [CategoryGroup] (nullable = Ungrouped); the
 * group drives reporting: charts and summary tables render one section per group, and totals are
 * summed as-is (no debit/credit split). A future `parentId` would turn this into a subcategory tree.
 */
@Entity
@Table(name = "categories")
class Category(
    @Column(nullable = false, unique = true)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var group: CategoryGroup? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
