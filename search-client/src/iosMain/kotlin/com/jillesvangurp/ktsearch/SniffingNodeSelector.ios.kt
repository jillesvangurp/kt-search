package com.jillesvangurp.ktsearch

/**
 * On JVM this will return the current thread name,  otherwise this will return null and pick a random node
 */
actual fun threadId(): String? = null