package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.jillesvangurp.ktsearch.ClusterStatus
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class KtSearchCliTest {
    @Test
    fun statusReturnsZeroForYellowCluster() = runTest {
        val service = FakeService(
            status = StatusResult(
                clusterName = "demo",
                status = ClusterStatus.Yellow,
                timedOut = false,
            )
        )
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("status"))

        service.statusCalls shouldBe 1
    }

    @Test
    fun statusReturnsNonZeroForRedCluster() = runTest {
        val service = FakeService(
            status = StatusResult(
                clusterName = "demo",
                status = ClusterStatus.Red,
                timedOut = false,
            )
        )
        val cmd = newCommand(service = service)

        val result = runCatching {
            cmd.parse(arrayOf("status"))
        }.exceptionOrNull()

        (result is ProgramResult) shouldBe true
        (result as ProgramResult).statusCode shouldBe 2
    }

    @Test
    fun dumpFailsWhenOutputExistsWithoutYesInNonInteractiveMode() = runTest {
        val service = FakeService()
        val platform = FakePlatform(
            interactive = false,
            existingPaths = mutableSetOf("products.ndjson.gz"),
        )
        val cmd = newCommand(service = service, platform = platform)

        val result = runCatching {
            cmd.parse(arrayOf("index", "dump", "products"))
        }.exceptionOrNull()

        (result is UsageError) shouldBe true
    }

    @Test
    fun dumpWritesSourcesToGzipNdjson() {
        val path = createTempFile("ktsearch-cli-", ".ndjson.gz").toFile()
        path.delete()

        val writer = platformCreateGzipWriter(path.absolutePath)
        writer.writeLine("{\"id\":1}")
        writer.writeLine("{\"id\":2}")
        writer.close()

        val lines = readGzipLines(path)

        lines.size shouldBe 2
        lines[0] shouldBe "{\"id\":1}"
        lines[1] shouldBe "{\"id\":2}"
    }

    @Test
    fun dumpCommandStreamsToWriter() = runTest {
        val service = FakeService(
            dumpLines = listOf("{\"id\":1}", "{\"id\":2}", "{\"id\":3}"),
        )
        val platform = FakePlatform(interactive = false)
        val cmd = newCommand(service = service, platform = platform)

        cmd.parse(arrayOf("index", "dump", "products", "--yes"))

        service.lastDumpedIndex shouldBe "products"
        platform.lastWriter.lines.size shouldBe 3
    }

    @Test
    fun statusUsesGlobalConnectionOptions() = runTest {
        val service = FakeService(
            status = StatusResult(
                clusterName = "demo",
                status = ClusterStatus.Green,
                timedOut = false,
            )
        )
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "--host", "search.internal",
                "--port", "9443",
                "--https",
                "--user", "bob",
                "--password", "secret",
                "--logging",
                "status",
            )
        )

        service.lastConnectionOptions shouldBe ConnectionOptions(
            host = "search.internal",
            port = 9443,
            https = true,
            user = "bob",
            password = "secret",
            logging = true,
        )
    }

    private fun newCommand(
        service: FakeService,
        platform: CliPlatform = FakePlatform(),
    ): KtSearchCommand {
        return KtSearchCommand(service = service, platform = platform)
    }

    private fun readGzipLines(file: File): List<String> {
        GZIPInputStream(FileInputStream(file)).use { gzip ->
            InputStreamReader(gzip).buffered().use { reader ->
                return reader.readLines()
            }
        }
    }
}

private class FakeService(
    private val status: StatusResult =
        StatusResult("cluster", ClusterStatus.Green, timedOut = false),
    private val dumpLines: List<String> = listOf("{\"id\":1}"),
) : CliService {
    var lastConnectionOptions: ConnectionOptions? = null
    var lastDumpedIndex: String? = null
    var statusCalls: Int = 0

    override suspend fun fetchStatus(connectionOptions: ConnectionOptions): StatusResult {
        statusCalls++
        lastConnectionOptions = connectionOptions
        return status
    }

    override suspend fun dumpIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        writer: NdjsonGzipWriter,
    ): Long {
        lastConnectionOptions = connectionOptions
        lastDumpedIndex = index
        dumpLines.forEach { writer.writeLine(it) }
        return dumpLines.size.toLong()
    }
}

private class FakePlatform(
    private val interactive: Boolean = false,
    private val existingPaths: MutableSet<String> = mutableSetOf(),
) : CliPlatform {
    val lastWriter = RecordingWriter()

    override fun fileExists(path: String): Boolean = existingPaths.contains(path)

    override fun isInteractiveInput(): Boolean = interactive

    override fun readLineFromStdin(): String? = "y"

    override fun createGzipWriter(path: String): NdjsonGzipWriter {
        return lastWriter
    }
}

private class RecordingWriter : NdjsonGzipWriter {
    val lines = mutableListOf<String>()

    override fun writeLine(line: String) {
        lines.add(line)
    }

    override fun close() {
    }
}
