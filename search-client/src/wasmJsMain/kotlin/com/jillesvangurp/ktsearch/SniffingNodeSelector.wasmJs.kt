package com.jillesvangurp.ktsearch

/**
 * On JVM this will return the current thread name,  on kotlin-js this will return null
 */
actual fun threadId(): String? {
    return null
}