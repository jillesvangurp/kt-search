package com.jillesvangurp.ktsearch

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.Duration


actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
    ): HttpClient {
    // We experienced some threading issues with CIO. Java engine seems more stable currently.
    return ktorClientWithJavaEngine(logging, user, password, elasticApiKey)
}
fun ktorClientWithJavaEngine(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
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
        if(!user.isNullOrBlank() && !password.isNullOrBlank()) {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(user, password)
                    }
                    sendWithoutRequest {
                        true
                    }
                }
            }
        }
        if(!elasticApiKey.isNullOrBlank()) {
            headers {
                append("Authorization", "ApiKey $elasticApiKey")
            }
        }

        install(ContentNegotiation) {
            json(DEFAULT_JSON)
        }
        if (logging) {
            install(Logging) {
                level = LogLevel.ALL
            }
        }

    }
}

fun ktorClientWithCIOEngine(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
) = HttpClient(CIO) {
    engine {
        maxConnectionsCount = 100
        endpoint {
            keepAliveTime = 100_000
            connectTimeout = 5_000
            requestTimeout = 30_000
            connectAttempts = 3
        }
    }
    if (!user.isNullOrBlank() && !password.isNullOrBlank()) {
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(user, password)
                }
                sendWithoutRequest {
                    true
                }
            }
        }
    }
    if(!elasticApiKey.isNullOrBlank()) {
        headers {
            append("Authorization", "ApiKey $elasticApiKey")
        }
    }
    if (logging) {
        install(Logging) {
            level = LogLevel.ALL
        }
    }
    install(ContentNegotiation) {
        json(DEFAULT_JSON)
    }
}