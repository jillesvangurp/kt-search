package com.jillesvangurp.ktsearch

actual fun simpleIndexProvider(initialIndex: Int): IndexProvider = object: IndexProvider {
    private var index: Int = initialIndex

    override fun get(): Int = index

    override fun set(value: Int) {
        index = value
    }
}
