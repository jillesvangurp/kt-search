package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.status.InfoStatus
import ch.qos.logback.core.status.WarnStatus
import org.slf4j.LoggerFactory

// Logback status messages bypass appenders and cannot feed back into this indexer.
internal fun diagnostic(message: String, cause: Throwable? = null) {
    (LoggerFactory.getILoggerFactory() as? LoggerContext)?.statusManager
        ?.add(WarnStatus(message, "kt-search-logback", cause))
}

internal fun log(message: String) {
    (LoggerFactory.getILoggerFactory() as? LoggerContext)?.statusManager
        ?.add(InfoStatus(message, "kt-search-logback"))
}
