package com.jillesvangurp.ktsearch

class SimpleIndexProvider(private var index: Int) : IndexProvider {
    override fun get(): Int = index

    override fun set(value: Int) {
        index = value
    }
}

actual fun indexProvider(initialIndex: Int): IndexProvider = SimpleIndexProvider(initialIndex)
