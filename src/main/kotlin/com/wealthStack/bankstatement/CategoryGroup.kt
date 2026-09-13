package com.wealthStack.bankstatement

import jakarta.persistence.*

/**
 * A user-managed grouping that categories are bucketed into. Groups replace the old fixed
 * `CategoryType` enum (spending/income/others): the user fully owns them (create / rename / delete)
 * and reporting renders one chart/table section per group. A category may belong to no group
 * (Ungrouped), the way an operation may be Uncategorized.
 */
@Entity
@Table(name = "category_groups")
class CategoryGroup(
    @Column(nullable = false, unique = true)
    var name: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
