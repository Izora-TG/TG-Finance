package com.tgfinance.app.util

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction over the clock so that repositories and view models are testable. */
interface TimeProvider {
    fun now(): Long
    fun today(): LocalDate
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}

interface IdGenerator {
    fun newId(): String
}

@Singleton
class UuidGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
}
