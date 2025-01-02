package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl

actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
    block: HttpClientConfig<*>.()->Unit
): HttpClient {
  return HttpClient(Curl) {
    block(this)
  }
}
