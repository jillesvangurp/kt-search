package com.jillesvangurp.ktsearch

class ThreadLocalIndex(initialIndex: Int) : IndexProvider {
    private val index = ThreadLocal.withInitial { initialIndex }

    override fun get(): Int = index.get()

    override fun set(value: Int) = index.set(value)
}

actual fun indexProvider(initialIndex: Int): IndexProvider = ThreadLocalIndex(initialIndex)
