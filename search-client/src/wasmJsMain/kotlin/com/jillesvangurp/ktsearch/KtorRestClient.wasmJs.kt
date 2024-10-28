package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient

actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?
): HttpClient {
    return HttpClient()
}