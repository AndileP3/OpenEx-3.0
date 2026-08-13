package com.openex.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.util.UUID

// One row per (account_id, asset). DB has a CHECK (amount >= 0) constraint as
// a hard backstop in addition to the application-level check in WalletService
// -- this is the "no negative balances" constraint called out on Day 2.
@Entity
@Table(
    name = "balances",
    uniqueConstraints = [UniqueConstraint(columnNames = ["account_id", "asset"])]
)
class Balance(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(nullable = false)
    val asset: String,

    @Column(nullable = false, precision = 24, scale = 8)
    var amount: BigDecimal = BigDecimal.ZERO
)
