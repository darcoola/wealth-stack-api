package com.wealthStack.bankstatement

import jakarta.persistence.*

/**
 * A user-curated classification an operation can be tagged with. Categories form an editable
 * dictionary the user fully owns (create / rename / delete) — they are no longer derived from a
 * bank's own transaction type. A future `parentId` would turn this into a subcategory tree.
 */
@Entity
@Table(name = "categories")
class Category(
    @Column(nullable = false, unique = true)
    var name: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)
