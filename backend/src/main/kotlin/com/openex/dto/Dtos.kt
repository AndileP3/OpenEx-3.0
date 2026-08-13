package com.openex.dto

import com.openex.domain.OrderSide
import com.openex.domain.OrderType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class CreateAccountRequest(@field:NotBlank val name: String)

data class AccountResponse(val id: UUID, val name: String)

data class DepositWithdrawRequest(
    @field:NotNull val accountId: UUID,
    @field:NotBlank val asset: String,
    @field:DecimalMin(value = "0.00000001") val amount: BigDecimal
)

data class PlaceOrderRequest(
    @field:NotNull val accountId: UUID,
    @field:NotBlank val symbol: String,
    @field:NotNull val side: OrderSide,
    @field:NotNull val type: OrderType,
    val price: BigDecimal? = null,
    @field:DecimalMin(value = "0.00000001") val quantity: BigDecimal
)

data class PriceLevelDto(val price: BigDecimal, val quantity: BigDecimal)

data class OrderBookSnapshot(val symbol: String, val bids: List<PriceLevelDto>, val asks: List<PriceLevelDto>)
