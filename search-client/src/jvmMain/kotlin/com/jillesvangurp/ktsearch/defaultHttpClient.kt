package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.engine.java.Java
import java.time.Duration


actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
    block: HttpClientConfig<*>.()->Unit
    ): HttpClient {
    // We experienced some threading issues with CIO. Java engine seems more stable currently.
    return ktorClientWithJavaEngine(logging, user, password, elasticApiKey, block)
}
fun ktorClientWithJavaEngine(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient {
    return HttpClient(Java) {
        // note the Java engine uses the IO dispatcher
        // you may want to bump the number of threads
        // for that by setting the system property
        // kotlinx.coroutines.io.parallelism=128
        engine {
            config {
                connectTimeout(Duration.ofSeconds(5))
            }
            pipelining = true
        }
        block(this)
    }
}

fun ktorClientWithCIOEngine(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
    block: HttpClientConfig<*>.() -> Unit,
) = HttpClient(CIO) {
    // there are some known issues with using CIO and weird EOF errors in combination with pipelining
    engine {
        maxConnectionsCount = 100
        endpoint {
            keepAliveTime = 100_000
            connectTimeout = 5_000
            requestTimeout = 30_000
            connectAttempts = 3
        }
    }
    block(this)
}