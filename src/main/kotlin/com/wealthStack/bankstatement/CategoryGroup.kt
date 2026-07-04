package com.wealthStack.bankstatement

import jakarta.persistence.*

/**
 * A user-managed grouping that categories are bucketed into. Groups replace the old fixed
 * `CategoryType` enum (spending/income/others): the user fully owns them (create / rename / delete)
 * and reporting renders one chart/table section per group. A category may belong to no group
 * (Ungrouped), the way an operation may be Uncategorized.
 */
@Entity
@Table(
    name = "category_groups",
    uniqueConstraints = [UniqueConstraint(name = "uk_category_groups_name", columnNames = ["party_id", "name"])]
)
class CategoryGroup(
    @Column(nullable = false)
    var name: String,

    /** Owning party — names are unique per party, not globally. */
    @Column(name = "party_id", nullable = false)
    var partyId: Long = 0,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
