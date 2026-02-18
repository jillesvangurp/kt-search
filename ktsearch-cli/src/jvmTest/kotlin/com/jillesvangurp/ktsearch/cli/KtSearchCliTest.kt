package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.cli.command.root.KtSearchCommand
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

    @Test
    fun searchRequiresEitherQueryOrData() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        val result = runCatching {
            cmd.parse(arrayOf("index", "search", "products"))
        }.exceptionOrNull()

        (result is UsageError) shouldBe true
    }

    @Test
    fun searchRejectsBothQueryAndData() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        val result = runCatching {
            cmd.parse(
                arrayOf(
                    "index",
                    "search",
                    "products",
                    "--query",
                    "name:apple",
                    "--data",
                    """{"query":{"match_all":{}}}""",
                )
            )
        }.exceptionOrNull()

        (result is UsageError) shouldBe true
    }

    @Test
    fun searchForwardsQueryOptions() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "index",
                "search",
                "products",
                "--query",
                "name:apple",
                "--size",
                "30",
                "--offse",
                "10",
                "--fields",
                "name,tags",
                "--sort",
                "timestamp:desc,_id:asc",
                "--track-total-hits",
                "true",
                "--timeout",
                "30s",
                "--routing",
                "r1",
                "--preference",
                "_local",
                "--allow-partial-results",
                "false",
                "--profile",
                "--explain",
                "--terminate-after",
                "7",
                "--search-type",
                "query_then_fetch",
            )
        )

        service.lastSearchRequest shouldBe SearchRequest(
            index = "products",
            query = "name:apple",
            data = null,
            size = 30,
            offset = 10,
            fields = listOf("name", "tags"),
            sort = "timestamp:desc,_id:asc",
            trackTotalHits = true,
            timeout = "30s",
            routing = "r1",
            preference = "_local",
            allowPartialResults = false,
            profile = true,
            explain = true,
            terminateAfter = 7,
            searchType = "query_then_fetch",
        )
    }

    @Test
    fun catHealthUsesTableOutputByDefault() = runTest {
        val service = FakeService(
            catResponse = """[{"epoch":"1732817716","status":"green"}]""",
        )
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("cat", "health"))

        service.lastCatRequest shouldBe CatServiceRequest(
            variant = CatVariant.Health,
            target = null,
            columns = null,
            sort = null,
            verbose = false,
            help = false,
            bytes = null,
            time = null,
            local = null,
        )
    }

    @Test
    fun catIndicesForwardsOptionsAndTarget() = runTest {
        val service = FakeService(
            catResponse = """[{"health":"green","index":"products"}]""",
        )
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "cat",
                "indices",
                "products-*",
                "--columns",
                "health,index",
                "--sort",
                "health,index",
                "--verbose",
                "--bytes",
                "mb",
                "--time",
                "ms",
                "--local",
                "true",
            )
        )

        service.lastCatRequest shouldBe CatServiceRequest(
            variant = CatVariant.Indices,
            target = "products-*",
            columns = listOf("health", "index"),
            sort = listOf("health", "index"),
            verbose = true,
            help = false,
            bytes = "mb",
            time = "ms",
            local = true,
        )
    }

    @Test
    fun catHealthSupportsCsvFlag() = runTest {
        val service = FakeService(
            catResponse = """[{"status":"green"}]""",
        )
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("cat", "health", "--csv"))

        service.lastCatRequest?.variant shouldBe CatVariant.Health
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
    private val catResponse: String = "[]",
) : CliService {
    var lastConnectionOptions: ConnectionOptions? = null
    var lastDumpedIndex: String? = null
    var statusCalls: Int = 0
    var lastSearchRequest: SearchRequest? = null
    var lastCatRequest: CatServiceRequest? = null

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

    override suspend fun searchIndexRaw(
        connectionOptions: ConnectionOptions,
        index: String,
        query: String?,
        data: String?,
        size: Int,
        offset: Int,
        fields: List<String>?,
        sort: String?,
        trackTotalHits: Boolean?,
        timeout: String?,
        routing: String?,
        preference: String?,
        allowPartialResults: Boolean?,
        profile: Boolean,
        explain: Boolean,
        terminateAfter: Int?,
        searchType: String?,
    ): String {
        lastConnectionOptions = connectionOptions
        lastSearchRequest = SearchRequest(
            index = index,
            query = query,
            data = data,
            size = size,
            offset = offset,
            fields = fields,
            sort = sort,
            trackTotalHits = trackTotalHits,
            timeout = timeout,
            routing = routing,
            preference = preference,
            allowPartialResults = allowPartialResults,
            profile = profile,
            explain = explain,
            terminateAfter = terminateAfter,
            searchType = searchType,
        )
        return """{"hits":{"total":{"value":0,"relation":"eq"},"hits":[]}}"""
    }

    override suspend fun cat(
        connectionOptions: ConnectionOptions,
        request: CatRequest,
    ): String {
        lastConnectionOptions = connectionOptions
        lastCatRequest = CatServiceRequest(
            variant = request.variant,
            target = request.target,
            columns = request.columns,
            sort = request.sort,
            verbose = request.verbose,
            help = request.help,
            bytes = request.bytes,
            time = request.time,
            local = request.local,
        )
        return catResponse
    }
}

private data class SearchRequest(
    val index: String,
    val query: String?,
    val data: String?,
    val size: Int,
    val offset: Int,
    val fields: List<String>?,
    val sort: String?,
    val trackTotalHits: Boolean?,
    val timeout: String?,
    val routing: String?,
    val preference: String?,
    val allowPartialResults: Boolean?,
    val profile: Boolean,
    val explain: Boolean,
    val terminateAfter: Int?,
    val searchType: String?,
)

private data class CatServiceRequest(
    val variant: CatVariant,
    val target: String?,
    val columns: List<String>?,
    val sort: List<String>?,
    val verbose: Boolean?,
    val help: Boolean?,
    val bytes: String?,
    val time: String?,
    val local: Boolean?,
)

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
