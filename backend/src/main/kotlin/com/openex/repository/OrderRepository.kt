package com.openex.repository

import com.openex.domain.Order
import com.openex.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findBySymbolAndStatusInOrderByCreatedAtAsc(symbol: String, statuses: List<OrderStatus>): List<Order>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<Order>
    fun findByIdempotencyKey(idempotencyKey: String): Order?
}
