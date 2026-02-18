package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.ClusterStatus

/** Shared connection options used by all CLI commands. */
data class ConnectionOptions(
    val host: String,
    val port: Int,
    val https: Boolean,
    val user: String?,
    val password: String?,
    val logging: Boolean,
)

/** Result object for cluster status checks. */
data class StatusResult(
    val clusterName: String,
    val status: ClusterStatus,
    val timedOut: Boolean,
)

interface CliPlatform {
    fun fileExists(path: String): Boolean

    fun isInteractiveInput(): Boolean

    fun readLineFromStdin(): String?

    fun createGzipWriter(path: String): NdjsonGzipWriter
}

expect fun platformFileExists(path: String): Boolean

expect fun platformIsInteractiveInput(): Boolean

expect fun platformReadLineFromStdin(): String?

expect fun platformCreateGzipWriter(path: String): NdjsonGzipWriter

expect fun platformWriteUtf8File(path: String, content: String)

interface NdjsonGzipWriter {
    fun writeLine(line: String)

    fun close()
}

object DefaultCliPlatform : CliPlatform {
    override fun fileExists(path: String): Boolean = platformFileExists(path)

    override fun isInteractiveInput(): Boolean = platformIsInteractiveInput()

    override fun readLineFromStdin(): String? = platformReadLineFromStdin()

    override fun createGzipWriter(path: String): NdjsonGzipWriter =
        platformCreateGzipWriter(path)
}
