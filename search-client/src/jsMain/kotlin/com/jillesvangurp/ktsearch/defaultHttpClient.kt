package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.headers

actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
    block: HttpClientConfig<*>.()->Unit
): HttpClient {
    return HttpClient(Js) {
        block(this)
    }
}