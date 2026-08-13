package com.openex.service

import com.openex.domain.Balance
import com.openex.domain.LedgerEntry
import com.openex.domain.LedgerType
import com.openex.exception.InsufficientFundsException
import com.openex.repository.BalanceRepository
import com.openex.repository.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

data class AccountAsset(val accountId: UUID, val asset: String) : Comparable<AccountAsset> {
    override fun compareTo(other: AccountAsset): Int {
        val byAccount = accountId.compareTo(other.accountId)
        return if (byAccount != 0) byAccount else asset.compareTo(other.asset)
    }
}

@Service
class WalletService(
    private val balanceRepository: BalanceRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    /** Locks (or creates) the balance row for (accountId, asset). Must be called within a transaction. */
    private fun lockOrCreateBalance(key: AccountAsset): Balance {
        balanceRepository.findForUpdate(key.accountId, key.asset)?.let { return it }
        // Row doesn't exist yet - create it. A concurrent transaction could
        // race to insert the same row; the unique constraint on
        // (account_id, asset) will cause one of them to fail with a
        // constraint violation, which is acceptable here since deposits are
        // not high-contention on account creation.
        return balanceRepository.save(Balance(accountId = key.accountId, asset = key.asset, amount = BigDecimal.ZERO))
    }

    private fun applyDelta(balance: Balance, delta: BigDecimal, type: LedgerType, refId: String?, groupId: UUID) {
        val newAmount = balance.amount.add(delta)
        if (newAmount < BigDecimal.ZERO) {
            throw InsufficientFundsException(balance.asset)
        }
        balance.amount = newAmount
        balanceRepository.save(balance)
        ledgerEntryRepository.save(
            LedgerEntry(
                accountId = balance.accountId,
                asset = balance.asset,
                amount = delta,
                balanceAfter = newAmount,
                type = type,
                refId = refId,
                groupId = groupId
            )
        )
    }

    @Transactional
    fun deposit(accountId: UUID, asset: String, amount: BigDecimal): UUID {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        val groupId = UUID.randomUUID()
        val balance = lockOrCreateBalance(AccountAsset(accountId, asset))
        applyDelta(balance, amount, LedgerType.DEPOSIT, null, groupId)
        return groupId
    }

    @Transactional
    fun withdraw(accountId: UUID, asset: String, amount: BigDecimal): UUID {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        val groupId = UUID.randomUUID()
        val balance = lockOrCreateBalance(AccountAsset(accountId, asset))
        applyDelta(balance, amount.negate(), LedgerType.WITHDRAW, null, groupId)
        return groupId
    }

    /**
     * Settles a matched trade: buyer pays quote asset (e.g. USD) and receives
     * base asset (e.g. BTC); seller is the mirror image. All four legs post
     * atomically in one DB transaction, sharing a groupId.
     *
     * Locks are acquired in a globally consistent order (sorted by account id
     * then asset) regardless of who is buyer/seller, so two concurrent trades
     * that touch overlapping accounts can never deadlock against each other.
     */
    @Transactional
    fun settleTrade(
        buyerAccountId: UUID,
        sellerAccountId: UUID,
        baseAsset: String,
        quoteAsset: String,
        quantity: BigDecimal,
        price: BigDecimal,
        tradeId: String
    ) {
        val quoteAmount = quantity.multiply(price)
        val groupId = UUID.randomUUID()

        val keys = listOf(
            AccountAsset(buyerAccountId, quoteAsset),
            AccountAsset(sellerAccountId, quoteAsset),
            AccountAsset(sellerAccountId, baseAsset),
            AccountAsset(buyerAccountId, baseAsset)
        ).distinct().sorted()

        val locked = keys.associateWith { lockOrCreateBalance(it) }

        applyDelta(locked.getValue(AccountAsset(buyerAccountId, quoteAsset)), quoteAmount.negate(), LedgerType.TRADE, tradeId, groupId)
        applyDelta(locked.getValue(AccountAsset(sellerAccountId, quoteAsset)), quoteAmount, LedgerType.TRADE, tradeId, groupId)
        applyDelta(locked.getValue(AccountAsset(sellerAccountId, baseAsset)), quantity.negate(), LedgerType.TRADE, tradeId, groupId)
        applyDelta(locked.getValue(AccountAsset(buyerAccountId, baseAsset)), quantity, LedgerType.TRADE, tradeId, groupId)
    }

    @Transactional(readOnly = true)
    fun getBalances(accountId: UUID): Map<String, BigDecimal> =
        balanceRepository.findByAccountId(accountId).associate { it.asset to it.amount }
}
