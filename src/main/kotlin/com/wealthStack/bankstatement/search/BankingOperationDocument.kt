package com.wealthStack.bankstatement.search

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "banking_operations")
class BankingOperationDocument(
    @Id
    val id: String, // fingerprint + "-" + occurrence

    @Field(type = FieldType.Text)
    val description: String,

    @Field(type = FieldType.Keyword)
    val account: String,

    @Field(type = FieldType.Keyword)
    val categoryId: Long
)
