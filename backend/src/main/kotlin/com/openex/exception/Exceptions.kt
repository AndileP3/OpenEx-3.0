package com.openex.exception

class InsufficientFundsException(asset: String) : RuntimeException("Insufficient $asset balance")

class NotFoundException(message: String) : RuntimeException(message)

class InvalidOrderException(message: String) : RuntimeException(message)
