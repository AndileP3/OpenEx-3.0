package com.openex.domain

enum class OrderSide { BUY, SELL }

enum class OrderType { LIMIT, MARKET }

enum class OrderStatus { OPEN, PARTIAL, FILLED, CANCELLED, REJECTED }

enum class LedgerType { DEPOSIT, WITHDRAW, TRADE }
