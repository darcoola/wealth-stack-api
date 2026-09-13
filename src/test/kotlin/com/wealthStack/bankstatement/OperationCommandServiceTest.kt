package com.wealthStack.bankstatement

import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.wealthStack.bankstatement.search.BankingOperationSearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import com.wealthStack.bankstatement.search.AutoCategorizationService

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class OperationCommandServiceTest {
    @MockitoBean
    lateinit var searchRepository: BankingOperationSearchRepository

    @MockitoBean
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @MockitoBean
    lateinit var autoCategorizationService: AutoCategorizationService


    @Autowired
    lateinit var service: OperationCommandService

    @Autowired
    lateinit var repository: BankingOperationRepository

    @BeforeEach
    fun clean() = repository.deleteAll()

    private fun persistOperation(additionalInfo: String? = null, fingerprint: String = ""): BankingOperation =
        repository.save(
            BankingOperation(
                date = LocalDate.of(2025, 1, 1),
                description = "Allegro",
                amount = BigDecimal("-120.50"),
                type = OperationType.DEBIT,
                bankName = "mbank",
                account = "ACME 111",
                additionalInfo = additionalInfo,
                fingerprint = fingerprint,
            )
        )

    @Test
    fun `adds a note to an operation that had none`() {
        val op = persistOperation()

        service.updateAdditionalInfo(op.id!!, "new phone case")

        assertThat(repository.findById(op.id!!).get().additionalInfo).isEqualTo("new phone case")
    }

    @Test
    fun `trims the note and clears it when blank`() {
        val op = persistOperation(additionalInfo = "old note")

        service.updateAdditionalInfo(op.id!!, "   ")

        assertThat(repository.findById(op.id!!).get().additionalInfo).isNull()
    }

    @Test
    fun `throws for an unknown operation`() {
        assertThrows<IllegalArgumentException> { service.updateAdditionalInfo(-1, "x") }
    }

    @Test
    fun `deleteEverything removes all operations and reports the count`() {
        persistOperation(fingerprint = "a")
        persistOperation(fingerprint = "b")
        persistOperation(fingerprint = "c")

        val deleted = service.deleteEverything()

        assertThat(deleted).isEqualTo(3L)
        assertThat(repository.count()).isEqualTo(0L)
    }
}
