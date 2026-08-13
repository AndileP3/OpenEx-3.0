package com.openex.controller

import com.openex.config.AppProperties
import com.openex.domain.Order
import com.openex.domain.OrderStatus
import com.openex.domain.OrderType
import com.openex.dto.OrderBookSnapshot
import com.openex.dto.PlaceOrderRequest
import com.openex.exception.InvalidOrderException
import com.openex.exception.NotFoundException
import com.openex.repository.OrderRepository
import com.openex.repository.TradeRepository
import com.openex.service.MatchingEngineService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api")
class OrderController(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository,
    private val matchingEngineService: MatchingEngineService,
    private val appProperties: AppProperties
) {

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    fun placeOrder(
        @Valid @RequestBody req: PlaceOrderRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?
    ): MatchingEngineService.SubmitResult {
        if (req.type == OrderType.LIMIT && req.price == null) {
            throw InvalidOrderException("price is required for limit orders")
        }

        idempotencyKey?.let { key ->
            orderRepository.findByIdempotencyKey(key)?.let { existing ->
                return MatchingEngineService.SubmitResult(existing, emptyList())
            }
        }

        val order = orderRepository.save(
            Order(
                accountId = req.accountId,
                symbol = req.symbol,
                side = req.side,
                type = req.type,
                price = req.price,
                quantity = req.quantity,
                remaining = req.quantity,
                idempotencyKey = idempotencyKey
            )
        )

        return matchingEngineService.submitOrder(order, appProperties.baseAsset, appProperties.quoteAsset)
    }

    @DeleteMapping("/orders/{id}")
    fun cancelOrder(@PathVariable id: UUID): Order {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order not found") }
        if (order.status != OrderStatus.OPEN && order.status != OrderStatus.PARTIAL) {
            throw InvalidOrderException("Cannot cancel order with status ${order.status}")
        }
        return matchingEngineService.cancelOrder(order)
    }

    @GetMapping("/orders/{id}")
    fun getOrder(@PathVariable id: UUID): Order =
        orderRepository.findById(id).orElseThrow { NotFoundException("Order not found") }

    @GetMapping("/accounts/{accountId}/orders")
    fun getOrdersForAccount(@PathVariable accountId: UUID): List<Order> =
        orderRepository.findByAccountIdOrderByCreatedAtDesc(accountId)

    @GetMapping("/orderbook/{symbol}")
    fun getOrderBook(@PathVariable symbol: String): OrderBookSnapshot =
        matchingEngineService.getSnapshot(symbol)

    @GetMapping("/trades/{symbol}")
    fun getTrades(@PathVariable symbol: String) =
        tradeRepository.findBySymbolOrderByCreatedAtDesc(symbol, PageRequest.of(0, 50))
}
