package com.openex.controller

import com.openex.dto.DepositWithdrawRequest
import com.openex.service.WalletService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/wallet")
class WalletController(
    private val walletService: WalletService
) {

    @GetMapping("/{accountId}")
    fun getBalances(@PathVariable accountId: UUID): Map<String, BigDecimal> =
        walletService.getBalances(accountId)

    @PostMapping("/deposit")
    fun deposit(@Valid @RequestBody req: DepositWithdrawRequest): Map<String, BigDecimal> {
        walletService.deposit(req.accountId, req.asset, req.amount)
        return walletService.getBalances(req.accountId)
    }

    @PostMapping("/withdraw")
    fun withdraw(@Valid @RequestBody req: DepositWithdrawRequest): Map<String, BigDecimal> {
        walletService.withdraw(req.accountId, req.asset, req.amount)
        return walletService.getBalances(req.accountId)
    }
}
