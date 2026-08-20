package com.openex.service

import com.openex.domain.Order
import com.openex.domain.OrderSide
import com.openex.domain.OrderStatus
import com.openex.domain.OrderType
import com.openex.domain.Trade
import com.openex.dto.OrderBookSnapshot
import com.openex.dto.PriceLevelDto
import com.openex.repository.OrderRepository
import com.openex.repository.TradeRepository
import com.openex.websocket.MarketDataWebSocketHandler
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// A single resting order sitting on the in-memory book.
data class BookEntry(
    val orderId: UUID,
    val accountId: UUID,
    val price: BigDecimal,
    var remaining: BigDecimal,
    val createdAt: Instant
)

// One order book per symbol. All mutation happens under `lock` so a single
// JVM instance never matches two orders concurrently against the same book
// (price-time priority requires strictly sequential processing).
class SymbolBook {
    val lock = Any()
    val bids = mutableListOf<BookEntry>() // sorted: highest price first, then oldest first
    val asks = mutableListOf<BookEntry>() // sorted: lowest price first, then oldest first

    fun sort() {
        bids.sortWith(compareByDescending<BookEntry> { it.price }.thenBy { it.createdAt })
        asks.sortWith(compareBy<BookEntry> { it.price }.thenBy { it.createdAt })
    }
}

@Service
class MatchingEngineService(
    private val orderRepository: OrderRepository,
    private val tradeRepository: TradeRepository,
    private val walletService: WalletService,
    private val wsHandler: MarketDataWebSocketHandler
) : ApplicationRunner {

    private val books = ConcurrentHashMap<String, SymbolBook>()

    private fun bookFor(symbol: String) = books.computeIfAbsent(symbol) { SymbolBook() }

    /** On startup, rebuild each symbol's in-memory book from open/partial orders in Postgres. */
    override fun run(args: ApplicationArguments) {
        val openOrders = orderRepository.findAll().filter { it.status == OrderStatus.OPEN || it.status == OrderStatus.PARTIAL }
        openOrders.groupBy { it.symbol }.forEach { (symbol, orders) ->
            val book = bookFor(symbol)
            synchronized(book.lock) {
                orders.filter { it.side == OrderSide.BUY && it.type == OrderType.LIMIT }
                    .mapTo(book.bids) { it.toBookEntry() }
                orders.filter { it.side == OrderSide.SELL && it.type == OrderType.LIMIT }
                    .mapTo(book.asks) { it.toBookEntry() }
                book.sort()
            }
        }
    }

    private fun Order.toBookEntry() = BookEntry(id, accountId, price!!, remaining, createdAt)

    fun getSnapshot(symbol: String, depth: Int = 20): OrderBookSnapshot {
        val book = bookFor(symbol)
        synchronized(book.lock) {
            fun aggregate(entries: List<BookEntry>, descending: Boolean = false): List<PriceLevelDto> =
                entries.groupBy { it.price }
                    .toSortedMap()
                    .let { levels -> if (descending) levels.toSortedMap(reverseOrder()) else levels }
                    .entries.take(depth)
                    .map { (price, list) -> PriceLevelDto(price, list.sumOf { it.remaining }) }

            val bidLevels = aggregate(book.bids, descending = true)
            val askLevels = aggregate(book.asks).take(depth)
            return OrderBookSnapshot(symbol, bidLevels, askLevels)
        }
    }

    data class SubmitResult(val order: Order, val trades: List<Trade>)

    /**
    * Places `orderDoc` (already persisted with status OPEN) against the book,
    * matching price-time priority. Settles funds via WalletService per fill.
    * An insufficient-funds error aborts the transaction so no unfunded order
    * or partial settlement is left behind.
     */
    @Transactional
    fun submitOrder(order: Order, baseAsset: String, quoteAsset: String): SubmitResult {
        val book = bookFor(order.symbol)
        val trades = mutableListOf<Trade>()

        synchronized(book.lock) {
            val isBuy = order.side == OrderSide.BUY
            val opposite = if (isBuy) book.asks else book.bids

            fun crosses(restingPrice: BigDecimal): Boolean =
                if (order.type == OrderType.MARKET) true
                else if (isBuy) order.price!! >= restingPrice else order.price!! <= restingPrice

            var remaining = order.remaining

            while (remaining > BigDecimal.ZERO && opposite.isNotEmpty() && crosses(opposite[0].price)) {
                val resting = opposite[0]
                val fillQty = remaining.min(resting.remaining)
                val fillPrice = resting.price

                val restingOrder = orderRepository.findById(resting.orderId).orElse(null)
                if (restingOrder == null || (restingOrder.status != OrderStatus.OPEN && restingOrder.status != OrderStatus.PARTIAL)) {
                    opposite.removeAt(0)
                    continue
                }

                val buyOrderId = if (isBuy) order.id else restingOrder.id
                val sellOrderId = if (isBuy) restingOrder.id else order.id
                val buyerAccountId = if (isBuy) order.accountId else restingOrder.accountId
                val sellerAccountId = if (isBuy) restingOrder.accountId else order.accountId

                walletService.settleTrade(
                    buyerAccountId = buyerAccountId,
                    sellerAccountId = sellerAccountId,
                    baseAsset = baseAsset,
                    quoteAsset = quoteAsset,
                    quantity = fillQty,
                    price = fillPrice,
                    tradeId = "${order.id}-${resting.orderId}"
                )

                remaining = remaining.subtract(fillQty)
                resting.remaining = resting.remaining.subtract(fillQty)

                restingOrder.remaining = resting.remaining
                restingOrder.status = if (resting.remaining == BigDecimal.ZERO) OrderStatus.FILLED else OrderStatus.PARTIAL
                orderRepository.save(restingOrder)
                if (resting.remaining == BigDecimal.ZERO) opposite.removeAt(0)

                val trade = tradeRepository.save(
                    Trade(
                        symbol = order.symbol,
                        price = fillPrice,
                        quantity = fillQty,
                        buyOrderId = buyOrderId,
                        sellOrderId = sellOrderId,
                        buyerAccountId = buyerAccountId,
                        sellerAccountId = sellerAccountId
                    )
                )
                trades.add(trade)
            }

            order.remaining = remaining
            order.status = when {
                remaining == BigDecimal.ZERO -> OrderStatus.FILLED
                remaining == order.quantity -> OrderStatus.OPEN
                else -> OrderStatus.PARTIAL
            }

            if (order.type == OrderType.MARKET) {
                if (remaining > BigDecimal.ZERO) order.status = OrderStatus.CANCELLED
            } else if (remaining > BigDecimal.ZERO) {
                (if (isBuy) book.bids else book.asks).add(order.toBookEntry())
                book.sort()
            }

            orderRepository.save(order)
        }

        // Broadcast after releasing nothing else needed - book lock already released.
        trades.forEach { wsHandler.broadcast("trade", it) }
        wsHandler.broadcast("orderbook", getSnapshot(order.symbol))

        return SubmitResult(order, trades)
    }

    fun cancelOrder(order: Order): Order {
        val book = bookFor(order.symbol)
        synchronized(book.lock) {
            val list = if (order.side == OrderSide.BUY) book.bids else book.asks
            list.removeIf { it.orderId == order.id }
        }
        order.status = OrderStatus.CANCELLED
        val saved = orderRepository.save(order)
        wsHandler.broadcast("orderbook", getSnapshot(order.symbol))
        return saved
    }
}
