package com.openex.repository

import com.openex.domain.Balance
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BalanceRepository : JpaRepository<Balance, UUID> {

    fun findByAccountId(accountId: UUID): List<Balance>

    // SELECT ... FOR UPDATE - locks the row for the duration of the
    // surrounding @Transactional method so concurrent trades/deposits on the
    // same (account, asset) serialize instead of racing.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.accountId = :accountId and b.asset = :asset")
    fun findForUpdate(@Param("accountId") accountId: UUID, @Param("asset") asset: String): Balance?
}
