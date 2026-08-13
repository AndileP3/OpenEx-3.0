package com.openex.controller

import com.openex.domain.Account
import com.openex.dto.AccountResponse
import com.openex.dto.CreateAccountRequest
import com.openex.exception.NotFoundException
import com.openex.repository.AccountRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountRepository: AccountRepository
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateAccountRequest): AccountResponse {
        val account = accountRepository.save(Account(name = req.name))
        return AccountResponse(account.id, account.name)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): AccountResponse {
        val account = accountRepository.findById(id).orElseThrow { NotFoundException("Account not found") }
        return AccountResponse(account.id, account.name)
    }
}
