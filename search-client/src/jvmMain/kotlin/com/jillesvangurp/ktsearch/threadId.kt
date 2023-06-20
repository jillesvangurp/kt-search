package com.jillesvangurp.ktsearch

actual fun threadId(): String? = Thread.currentThread().name