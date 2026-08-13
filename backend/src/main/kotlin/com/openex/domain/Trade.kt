package com.openex.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "trades")
class Trade(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false, precision = 24, scale = 8)
    val price: BigDecimal,

    @Column(nullable = false, precision = 24, scale = 8)
    val quantity: BigDecimal,

    @Column(name = "buy_order_id", nullable = false)
    val buyOrderId: UUID,

    @Column(name = "sell_order_id", nullable = false)
    val sellOrderId: UUID,

    @Column(name = "buyer_account_id", nullable = false)
    val buyerAccountId: UUID,

    @Column(name = "seller_account_id", nullable = false)
    val sellerAccountId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
