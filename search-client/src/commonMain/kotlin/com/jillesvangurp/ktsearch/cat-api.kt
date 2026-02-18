@file:Suppress("unused")

package com.jillesvangurp.ktsearch

/** Output format for cat endpoints. */
enum class CatFormat(val value: String) {
    Json("json"),
    Text("text"),
    Yaml("yaml"),
    Cbor("cbor"),
    Smile("smile"),
}

/** Unit used by cat endpoints for byte-size columns. */
enum class CatBytes(val value: String) {
    B("b"),
    Kb("kb"),
    Mb("mb"),
    Gb("gb"),
    Tb("tb"),
    Pb("pb"),
}

/** Unit used by cat endpoints for duration columns. */
enum class CatTime(val value: String) {
    D("d"),
    H("h"),
    M("m"),
    S("s"),
    Ms("ms"),
    Micros("micros"),
    Nanos("nanos"),
}

/**
 * Common cat query parameters.
 *
 * Use [headers] for `h`, [sort] for `s`, [verbose] for `v`, [help] for
 * `help`, [bytes] for `bytes`, [time] for `time`, [format] for `format`, and
 * [local] for `local`.
 */
data class CatRequestOptions(
    val headers: List<String>? = null,
    val sort: List<String>? = null,
    val verbose: Boolean? = null,
    val help: Boolean? = null,
    val bytes: CatBytes? = null,
    val time: CatTime? = null,
    val format: CatFormat? = null,
    val local: Boolean? = null,
    val extraParameters: Map<String, String>? = null,
)

/** `GET /_cat/aliases/{name?}`. */
suspend fun SearchClient.catAliases(
    name: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("aliases", name), options)

/** `GET /_cat/allocation/{node_id?}`. */
suspend fun SearchClient.catAllocation(
    nodeId: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("allocation", nodeId), options)

/** `GET /_cat/count/{index?}`. */
suspend fun SearchClient.catCount(
    target: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("count", target), options)

/** `GET /_cat/health`. */
suspend fun SearchClient.catHealth(
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("health"), options)

/** `GET /_cat/indices/{index?}`. */
suspend fun SearchClient.catIndices(
    target: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("indices", target), options)

/** `GET /_cat/master`. */
suspend fun SearchClient.catMaster(
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("master"), options)

/** `GET /_cat/nodes/{node_id?}`. */
suspend fun SearchClient.catNodes(
    nodeId: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("nodes", nodeId), options)

/** `GET /_cat/pending_tasks`. */
suspend fun SearchClient.catPendingTasks(
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("pending_tasks"), options)

/** `GET /_cat/recovery/{index?}`. */
suspend fun SearchClient.catRecovery(
    target: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("recovery", target), options)

/** `GET /_cat/repositories`. */
suspend fun SearchClient.catRepositories(
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("repositories"), options)

/** `GET /_cat/shards/{index?}`. */
suspend fun SearchClient.catShards(
    target: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("shards", target), options)

/** `GET /_cat/snapshots/{repository}`. */
suspend fun SearchClient.catSnapshots(
    repository: String = "_all",
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("snapshots", repository), options)

/** `GET /_cat/tasks`. */
suspend fun SearchClient.catTasks(
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("tasks"), options)

/** `GET /_cat/templates/{name?}`. */
suspend fun SearchClient.catTemplates(
    name: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("templates", name), options)

/** `GET /_cat/thread_pool/{patterns?}`. */
suspend fun SearchClient.catThreadPool(
    patterns: String? = null,
    options: CatRequestOptions = CatRequestOptions(),
): String = catRequest(listOf("thread_pool", patterns), options)

private suspend fun SearchClient.catRequest(
    pathParts: List<String?>,
    options: CatRequestOptions,
): String {
    return restClient.get {
        path(*listOf("_cat").plus(pathParts.filterNotNull()).toTypedArray())
        parameter("h", options.headers?.joinToString(","))
        parameter("s", options.sort?.joinToString(","))
        parameter("v", options.verbose)
        parameter("help", options.help)
        parameter("bytes", options.bytes?.value)
        parameter("time", options.time?.value)
        parameter("format", options.format?.value)
        parameter("local", options.local)
        parameters(options.extraParameters)
    }.getOrThrow().text
}
