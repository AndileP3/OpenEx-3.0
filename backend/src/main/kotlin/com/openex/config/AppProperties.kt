package com.openex.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val symbol: String = "BTC-USD",
    val baseAsset: String = "BTC",
    val quoteAsset: String = "USD",
    val seedData: Boolean = false
)
