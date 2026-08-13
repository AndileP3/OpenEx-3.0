package com.openex.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// Every balance mutation writes one LedgerEntry per leg. A deposit writes one
// entry; a withdrawal writes one entry; a trade settlement writes four
// entries (buyer debit quote, seller credit quote, seller debit base, buyer
// credit base) all sharing the same groupId so they can be reconciled as a
// single atomic event. This is the double-entry audit trail from Day 2/3.
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(nullable = false)
    val asset: String,

    @Column(nullable = false, precision = 24, scale = 8)
    val amount: BigDecimal, // positive = credit, negative = debit

    @Column(name = "balance_after", nullable = false, precision = 24, scale = 8)
    val balanceAfter: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: LedgerType,

    @Column(name = "ref_id")
    val refId: String? = null,

    @Column(name = "group_id", nullable = false)
    val groupId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
