package com.jillesvangurp.ktsearch

actual fun threadId(): String? {
    // javascript has no threads, use AffinityId scope if you want different nodes
    return null
}