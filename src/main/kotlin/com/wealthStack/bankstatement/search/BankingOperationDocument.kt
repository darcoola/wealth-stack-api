package com.wealthStack.bankstatement.search

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "banking_operations")
class BankingOperationDocument(
    @Id
    val id: String, // partyId + "-" + fingerprint + "-" + occurrence (fingerprints repeat across parties)

    @Field(type = FieldType.Text)
    val description: String,

    @Field(type = FieldType.Keyword)
    val account: String,

    @Field(type = FieldType.Keyword)
    val categoryId: Long,

    /** Owning party; every prediction query filters on it so suggestions never cross parties. */
    @Field(type = FieldType.Keyword)
    val partyId: Long
)
