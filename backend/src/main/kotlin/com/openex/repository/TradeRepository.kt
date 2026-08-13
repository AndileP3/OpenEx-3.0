package com.openex.repository

import com.openex.domain.Trade
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeRepository : JpaRepository<Trade, UUID> {
    fun findBySymbolOrderByCreatedAtDesc(symbol: String, pageable: Pageable): List<Trade>
}
