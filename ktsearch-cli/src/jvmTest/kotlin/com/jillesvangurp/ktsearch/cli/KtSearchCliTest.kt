package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.command.parse
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterTopApiSnapshot
import com.jillesvangurp.ktsearch.cli.command.root.KtSearchCommand
import com.jillesvangurp.ktsearch.cli.command.tasks.TaskProgress
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

class KtSearchCliTest {
    @Test
    fun clusterHealthReturnsZeroForYellowCluster() = runTest {
        val service = FakeService(
            clusterHealthResponse = """
                {
                  "cluster_name": "demo",
                  "status": "yellow",
                  "timed_out": false
                }
            """.trimIndent(),
        )
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("cluster", "health"))

        service.clusterHealthCalls shouldBe 1
    }

    @Test
    fun clusterHealthReturnsNonZeroForRedCluster() = runTest {
        val service = FakeService(
            clusterHealthResponse = """
                {
                  "cluster_name": "demo",
                  "status": "red",
                  "timed_out": false
                }
            """.trimIndent(),
        )
        val cmd = newCommand(service = service)

        val result = runCatching {
            cmd.parse(arrayOf("cluster", "health"))
        }.exceptionOrNull()

        (result is ProgramResult) shouldBe true
        (result as ProgramResult).statusCode shouldBe 2
    }

    @Test
    fun infoCallsRootEndpoint() = runTest {
        val service = FakeService(
            rootInfoResponse = """
                {
                  "name": "n1",
                  "cluster_name": "demo",
                  "version": {
                    "number": "9.0.0"
                  }
                }
            """.trimIndent(),
        )
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("info"))

        service.rootInfoCalls shouldBe 1
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
    fun clusterHealthUsesGlobalConnectionOptions() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "--host", "search.internal",
                "--port", "9443",
                "--https",
                "--user", "bob",
                "--password", "secret",
                "--elastic-api-key", "my-api-key",
                "--logging",
                "cluster",
                "health",
            )
        )

        service.lastConnectionOptions shouldBe ConnectionOptions(
            host = "search.internal",
            port = 9443,
            https = true,
            user = "bob",
            password = "secret",
            elasticApiKey = "my-api-key",
            logging = true,
        )
    }

    @Test
    fun clusterHealthAcceptsElasticApiKeyWithoutBasicAuth() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "--host", "search.internal",
                "--elastic-api-key", "my-api-key",
                "cluster",
                "health",
            )
        )

        service.lastConnectionOptions shouldBe ConnectionOptions(
            host = "search.internal",
            port = 9200,
            https = false,
            user = null,
            password = null,
            elasticApiKey = "my-api-key",
            logging = false,
        )
    }

    @Test
    fun clusterStatsCallsClusterStatsEndpoint() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("cluster", "stats"))

        service.lastApiRequest?.method shouldBe ApiMethod.Get
        service.lastApiRequest?.path shouldBe listOf("_cluster", "stats")
    }

    @Test
    fun clusterSettingsForwardsFlagsAsQueryParameters() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "cluster",
                "settings",
                "--include-defaults",
                "--flat-settings",
            ),
        )

        service.lastApiRequest?.path shouldBe listOf("_cluster", "settings")
        service.lastApiRequest?.parameters shouldBe
            mapOf("include_defaults" to "true", "flat_settings" to "true")
    }

    @Test
    fun clusterTopDefaultsToThreeSecondPolling() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("cluster", "top", "--samples", "1"))

        service.clusterTopCalls shouldBe 1
    }

    @Test
    fun clusterTopRejectsZeroInterval() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        val result = runCatching {
            cmd.parse(
                arrayOf(
                    "cluster",
                    "top",
                    "--interval-seconds",
                    "0",
                    "--samples",
                    "1",
                ),
            )
        }.exceptionOrNull()

        (result is UsageError) shouldBe true
    }

    @Test
    fun searchWithoutQueryOrDataIsAllowed() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("index", "search", "products"))

        service.lastSearchRequest shouldBe SearchRequest(
            index = "products",
            query = null,
            data = null,
            size = 50,
            offset = 0,
            fields = null,
            sort = null,
            trackTotalHits = null,
            timeout = null,
            routing = null,
            preference = null,
            allowPartialResults = null,
            profile = false,
            explain = false,
            terminateAfter = null,
            searchType = null,
        )
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

    @Test
    fun deleteIndexRequiresYesInNonInteractiveMode() = runTest {
        val service = FakeService()
        val platform = FakePlatform(interactive = false)
        val cmd = newCommand(service = service, platform = platform)

        val result = runCatching {
            cmd.parse(arrayOf("index", "delete", "products"))
        }.exceptionOrNull()

        (result is UsageError) shouldBe true
    }

    @Test
    fun refreshIndexCallsRefreshEndpoint() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "index",
                "refresh",
                "products",
                "--allow-no-indices",
                "true",
                "--expand-wildcards",
                "open",
                "--ignore-unavailable",
                "false",
            ),
        )

        service.lastApiRequest shouldBe ApiRequest(
            method = ApiMethod.Post,
            path = listOf("products", "_refresh"),
            parameters = mapOf(
                "allow_no_indices" to "true",
                "expand_wildcards" to "open",
                "ignore_unavailable" to "false",
            ),
            data = null,
        )
    }

    @Test
    fun aliasAddUsesAtomicAliasesApi() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("index", "alias", "add", "products-v1", "products"))

        service.lastApiRequest?.method shouldBe ApiMethod.Post
        service.lastApiRequest?.path shouldBe listOf("_aliases")
    }

    @Test
    fun restoreReadsInputAndForwardsOptions() = runTest {
        val service = FakeService()
        val platform = FakePlatform(
            interactive = false,
            existingPaths = mutableSetOf("products.ndjson.gz"),
        ).apply {
            nextReaderLines = listOf("""{"id":1}""", """{"id":2}""")
        }
        val cmd = newCommand(service = service, platform = platform)

        cmd.parse(
            arrayOf(
                "index",
                "restore",
                "products",
                "--bulk-size",
                "200",
                "--yes",
            ),
        )

        service.lastRestoreRequest shouldBe RestoreRequest(
            index = "products",
            bulkSize = 200,
            createIfMissing = true,
            recreate = false,
            refresh = "wait_for",
            pipeline = null,
            routing = null,
            idField = null,
            disableRefreshInterval = false,
            setReplicasToZero = false,
            lines = listOf("""{"id":1}""", """{"id":2}"""),
        )
    }

    @Test
    fun restoreForwardsTemporaryIndexingSettingsFlags() = runTest {
        val service = FakeService()
        val platform = FakePlatform(
            interactive = false,
            existingPaths = mutableSetOf("products.ndjson.gz"),
        ).apply {
            nextReaderLines = listOf("""{"id":1}""")
        }
        val cmd = newCommand(service = service, platform = platform)

        cmd.parse(
            arrayOf(
                "index",
                "restore",
                "products",
                "--disable-refresh-interval",
                "--set-replicas-zero",
                "--yes",
            ),
        )

        service.lastRestoreRequest?.disableRefreshInterval shouldBe true
        service.lastRestoreRequest?.setReplicasToZero shouldBe true
    }

    @Test
    fun docGetCallsDocEndpoint() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("index", "doc", "get", "products", "42"))

        service.lastApiRequest shouldBe ApiRequest(
            method = ApiMethod.Get,
            path = listOf("products", "_doc", "42"),
            parameters = null,
            data = null,
        )
    }

    @Test
    fun reindexWaitFalseSetsWaitForCompletionParameter() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "index",
                "reindex",
                "--wait",
                "false",
                "--data",
                """{"source":{"index":"a"},"dest":{"index":"b"}}""",
            ),
        )

        service.lastReindexRequest shouldBe ReindexRequest(
            body = """{"source":{"index":"a"},"dest":{"index":"b"}}""",
            waitForCompletion = false,
            disableRefreshInterval = false,
            setReplicasToZero = false,
            progressReporting = false,
        )
    }

    @Test
    fun reindexForwardsTemporaryIndexingSettingsFlags() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "index",
                "reindex",
                "--disable-refresh-interval",
                "--set-replicas-zero",
                "--data",
                """{"source":{"index":"a"},"dest":{"index":"b"}}""",
            ),
        )

        service.lastReindexRequest shouldBe ReindexRequest(
            body = """{"source":{"index":"a"},"dest":{"index":"b"}}""",
            waitForCompletion = false,
            disableRefreshInterval = true,
            setReplicasToZero = true,
            progressReporting = true,
        )
    }

    @Test
    fun reindexProgressReportingFlagEnablesProgressCallback() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "index",
                "reindex",
                "--progress-reporting",
                "--data",
                """{"source":{"index":"a"},"dest":{"index":"b"}}""",
            ),
        )

        service.lastReindexRequest shouldBe ReindexRequest(
            body = """{"source":{"index":"a"},"dest":{"index":"b"}}""",
            waitForCompletion = false,
            disableRefreshInterval = false,
            setReplicasToZero = false,
            progressReporting = true,
        )
    }

    @Test
    fun tasksStatusCallsTaskEndpoint() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(arrayOf("tasks", "status", "abc:123"))

        service.lastApiRequest shouldBe ApiRequest(
            method = ApiMethod.Get,
            path = listOf("_tasks", "abc:123"),
            parameters = null,
            data = null,
        )
    }

    @Test
    fun applyAutoDetectsIndexTemplate() = runTest {
        val service = FakeService()
        val cmd = newCommand(service = service)

        cmd.parse(
            arrayOf(
                "index",
                "apply",
                "logs-template",
                "--data",
                """{"index_patterns":["logs-*"],"template":{"settings":{"index.number_of_shards":1}}}""",
            ),
        )

        service.lastApiRequest?.path shouldBe
            listOf("_index_template", "logs-template")
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
    private val dumpLines: List<String> = listOf("{\"id\":1}"),
    private val catResponse: String = "[]",
    private val rootInfoResponse: String = """{"name":"node"}""",
    private val clusterHealthResponse: String = """
        {"cluster_name":"cluster","status":"green","timed_out":false}
    """.trimIndent(),
) : CliService {
    var lastConnectionOptions: ConnectionOptions? = null
    var lastDumpedIndex: String? = null
    var rootInfoCalls: Int = 0
    var clusterHealthCalls: Int = 0
    var lastSearchRequest: SearchRequest? = null
    var lastCatRequest: CatServiceRequest? = null
    var lastApiRequest: ApiRequest? = null
    var lastRestoreRequest: RestoreRequest? = null
    var lastReindexRequest: ReindexRequest? = null
    var clusterTopCalls: Int = 0

    override suspend fun fetchRootInfo(connectionOptions: ConnectionOptions): String {
        rootInfoCalls++
        lastConnectionOptions = connectionOptions
        return rootInfoResponse
    }

    override suspend fun fetchClusterHealth(
        connectionOptions: ConnectionOptions,
    ): String {
        clusterHealthCalls++
        lastConnectionOptions = connectionOptions
        return clusterHealthResponse
    }

    override suspend fun fetchClusterTopSnapshot(
        connectionOptions: ConnectionOptions,
    ): ClusterTopApiSnapshot {
        lastConnectionOptions = connectionOptions
        clusterTopCalls++
        return ClusterTopApiSnapshot(
            clusterStats = null,
            clusterHealth = null,
            nodesStats = null,
            errors = emptyList(),
            fetchedAt = Clock.System.now(),
        )
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

    override suspend fun apiRequest(
        connectionOptions: ConnectionOptions,
        method: ApiMethod,
        path: List<String>,
        parameters: Map<String, String>?,
        data: String?,
    ): String {
        lastConnectionOptions = connectionOptions
        lastApiRequest = ApiRequest(
            method = method,
            path = path,
            parameters = parameters,
            data = data,
        )
        return """{"acknowledged":true}"""
    }

    override suspend fun restoreIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        reader: NdjsonGzipReader,
        bulkSize: Int,
        createIfMissing: Boolean,
        recreate: Boolean,
        refresh: String,
        pipeline: String?,
        routing: String?,
        idField: String?,
        disableRefreshInterval: Boolean,
        setReplicasToZero: Boolean,
    ): Long {
        lastConnectionOptions = connectionOptions
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            lines.add(line)
        }
        lastRestoreRequest = RestoreRequest(
            index = index,
            bulkSize = bulkSize,
            createIfMissing = createIfMissing,
            recreate = recreate,
            refresh = refresh,
            pipeline = pipeline,
            routing = routing,
            idField = idField,
            disableRefreshInterval = disableRefreshInterval,
            setReplicasToZero = setReplicasToZero,
            lines = lines,
        )
        return lines.size.toLong()
    }

    override suspend fun reindex(
        connectionOptions: ConnectionOptions,
        body: String,
        waitForCompletion: Boolean,
        disableRefreshInterval: Boolean,
        setReplicasToZero: Boolean,
        onTaskProgress: ((TaskProgress) -> Unit)?,
    ): String {
        lastConnectionOptions = connectionOptions
        lastReindexRequest = ReindexRequest(
            body = body,
            waitForCompletion = waitForCompletion,
            disableRefreshInterval = disableRefreshInterval,
            setReplicasToZero = setReplicasToZero,
            progressReporting = onTaskProgress != null,
        )
        return """{"acknowledged":true}"""
    }

    override suspend fun indexExists(
        connectionOptions: ConnectionOptions,
        index: String,
    ): Boolean {
        lastConnectionOptions = connectionOptions
        return true
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

private data class ApiRequest(
    val method: ApiMethod,
    val path: List<String>,
    val parameters: Map<String, String>?,
    val data: String?,
)

private data class RestoreRequest(
    val index: String,
    val bulkSize: Int,
    val createIfMissing: Boolean,
    val recreate: Boolean,
    val refresh: String,
    val pipeline: String?,
    val routing: String?,
    val idField: String?,
    val disableRefreshInterval: Boolean,
    val setReplicasToZero: Boolean,
    val lines: List<String>,
)

private data class ReindexRequest(
    val body: String,
    val waitForCompletion: Boolean,
    val disableRefreshInterval: Boolean,
    val setReplicasToZero: Boolean,
    val progressReporting: Boolean,
)

private class FakePlatform(
    private val interactive: Boolean = false,
    private val existingPaths: MutableSet<String> = mutableSetOf(),
) : CliPlatform {
    val lastWriter = RecordingWriter()
    var nextReaderLines: List<String> = emptyList()

    override fun fileExists(path: String): Boolean = existingPaths.contains(path)

    override fun isInteractiveInput(): Boolean = interactive

    override fun consumeQuitKey(): Boolean = false

    override fun enableSingleKeyInput() {
    }

    override fun disableSingleKeyInput() {
    }

    override fun readLineFromStdin(): String? = "y"

    override fun createGzipWriter(path: String): NdjsonGzipWriter {
        return lastWriter
    }

    override fun createGzipReader(path: String): NdjsonGzipReader {
        return RecordingReader(nextReaderLines)
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

private class RecordingReader(
    lines: List<String>,
) : NdjsonGzipReader {
    private val iterator = lines.iterator()

    override fun readLine(): String? {
        return if (iterator.hasNext()) iterator.next() else null
    }

    override fun close() {
    }
}
