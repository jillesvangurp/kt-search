package com.jillesvangurp.ktsearchlogback

import io.github.oshai.kotlinlogging.KotlinLogging


private val logger = KotlinLogging.logger("kt-search_logback-appender")

internal fun log(message: String) {
    logger.info { message }
    println("INFO kt-search_logback-appender: $message")
}

internal fun warn(e: Exception, message: String = e.message ?: "warning") {
    logger.warn(e) { message }
    println("WARNING kt-search_logback-appender: $message")
}