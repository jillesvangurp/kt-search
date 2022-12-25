package com.jillesvangurp.ktsearch

import java.util.concurrent.atomic.AtomicInteger

actual fun simpleIndexProvider(initialIndex: Int): IndexProvider = object: IndexProvider {
    // use AtomicInteger for thread safety
    private val index = AtomicInteger(initialIndex)

    override fun get(): Int = index.get()

    override fun set(value: Int) = index.set(value)
}
