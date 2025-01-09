package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.curl.Curl

// FIXME intellij weirdness with this class on mac; none of the ktor stuff is found somehow
// of course it builds fine with gradle; no idea if this is actually usable
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
