package com.jillesvangurp.ktsearch

actual fun simpleIndexProvider(initialIndex: Int): IndexProvider = object : IndexProvider {
    private var index = 0

    override fun get(): Int = index

    override fun set(value: Int) {
        index = value
    }
}