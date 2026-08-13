package com.openex.seed

import com.openex.config.AppProperties
import com.openex.domain.Account
import com.openex.repository.AccountRepository
import com.openex.service.WalletService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal

// Seeds two demo accounts (Alice, Bob) with starting balances so you can log
// in and trade immediately. Only runs when app.seed-data=true (see
// application.yml / the -dev profile) so it never fires against a real
// deployment by accident. Idempotent: skips seeding if accounts already exist.
@Component
@Order(1)
class DevDataSeeder(
    private val accountRepository: AccountRepository,
    private val walletService: WalletService,
    private val appProperties: AppProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        if (!appProperties.seedData) return
        if (accountRepository.count() > 0) {
            log.info("[seed] accounts already exist, skipping seed")
            return
        }

        val alice = accountRepository.save(Account(name = "Alice"))
        val bob = accountRepository.save(Account(name = "Bob"))

        walletService.deposit(alice.id, "USD", BigDecimal("100000"))
        walletService.deposit(alice.id, "BTC", BigDecimal("5"))
        walletService.deposit(bob.id, "USD", BigDecimal("100000"))
        walletService.deposit(bob.id, "BTC", BigDecimal("5"))

        log.info("[seed] Alice account id: {}", alice.id)
        log.info("[seed] Bob account id:   {}", bob.id)
    }
}
