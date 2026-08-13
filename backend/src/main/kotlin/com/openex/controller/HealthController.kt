package com.openex.controller

import com.openex.config.AppProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val appProperties: AppProperties) {
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok", "symbol" to appProperties.symbol)
}
