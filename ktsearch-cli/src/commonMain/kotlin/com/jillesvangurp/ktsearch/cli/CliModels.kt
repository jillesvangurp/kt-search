package com.jillesvangurp.ktsearch.cli

/** Shared connection options used by all CLI commands. */
data class ConnectionOptions(
    val host: String,
    val port: Int,
    val https: Boolean,
    val user: String?,
    val password: String?,
    val elasticApiKey: String?,
    val logging: Boolean,
    val awsSigV4: Boolean,
    val awsRegion: String?,
    val awsService: String?,
    val awsProfile: String?,
)

enum class CatVariant {
    Aliases,
    Allocation,
    Count,
    Health,
    Indices,
    Master,
    Nodes,
    PendingTasks,
    Recovery,
    Repositories,
    Shards,
    Snapshots,
    Tasks,
    Templates,
    ThreadPool,
}

data class CatRequest(
    val variant: CatVariant,
    val target: String? = null,
    val columns: List<String>? = null,
    val sort: List<String>? = null,
    val verbose: Boolean? = null,
    val help: Boolean? = null,
    val bytes: String? = null,
    val time: String? = null,
    val local: Boolean? = null,
    val extraParameters: Map<String, String>? = null,
)

interface CliPlatform {
    fun fileExists(path: String): Boolean

    fun isInteractiveInput(): Boolean

    fun readLineFromStdin(): String?

    fun createGzipWriter(path: String): NdjsonGzipWriter

    fun createGzipReader(path: String): NdjsonGzipReader
}

expect fun platformFileExists(path: String): Boolean

expect fun platformIsInteractiveInput(): Boolean

expect fun platformReadLineFromStdin(): String?

expect fun platformCreateGzipWriter(path: String): NdjsonGzipWriter

expect fun platformCreateGzipReader(path: String): NdjsonGzipReader

expect fun platformReadUtf8File(path: String): String

expect fun platformWriteUtf8File(path: String, content: String)

/** Returns the value of environment variable [name] or null if absent. */
expect fun platformGetEnv(name: String): String?

interface NdjsonGzipWriter {
    fun writeLine(line: String)

    fun close()
}

interface NdjsonGzipReader {
    fun readLine(): String?

    fun close()
}

object DefaultCliPlatform : CliPlatform {
    override fun fileExists(path: String): Boolean = platformFileExists(path)

    override fun isInteractiveInput(): Boolean = platformIsInteractiveInput()

    override fun readLineFromStdin(): String? = platformReadLineFromStdin()

    override fun createGzipWriter(path: String): NdjsonGzipWriter =
        platformCreateGzipWriter(path)

    override fun createGzipReader(path: String): NdjsonGzipReader =
        platformCreateGzipReader(path)
}
