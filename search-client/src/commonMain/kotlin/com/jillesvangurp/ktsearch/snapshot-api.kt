package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import com.jillesvangurp.jsondsl.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SnapshotRepository : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    class Settings : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
        var bucket by property<String>()
        var endPoint by property<String>()
        var protocol by property<String>()
        var pathStyleAccess by property<Boolean>()
        var region by property<String>()
    }

    var type by property<String>()
    var verify by property<Boolean>()
    fun settings(block: Settings.() -> Unit) {
        this["settings"] = Settings().apply(block)
    }
}

suspend fun SearchClient.registerSnapshotRepository(repositoryName: String, repository: SnapshotRepository) =
    restClient.put {
        path("_snapshot", repositoryName)
        body = repository.json(true)
    }.parseJsonObject()


suspend fun SearchClient.registerSnapshotRepository(repositoryName: String, block: SnapshotRepository.() -> Unit) {
    val repo = SnapshotRepository().apply(block)
    registerSnapshotRepository(repositoryName, repo)
}

suspend fun SearchClient.verifySnapshotRepository(repositoryName: String) = restClient.post {
    path("_snapshot", repositoryName, "_verify")
}.parseJsonObject()

suspend fun SearchClient.getSnapshotRepository(repositoryName: String?) = restClient.get {
    path("_snapshot", repositoryName)
}.parseJsonObject()

suspend fun SearchClient.deleteSnapshotRepository(repositoryName: String) = restClient.delete { 
    path("_snapshot", repositoryName)
}.parseJsonObject()


suspend fun SearchClient.listSnapshots(repositoryName: String, pattern: String = "_all") = restClient.get {
    path("_snapshot", repositoryName, pattern)
}.parseJsonObject()


suspend fun SearchClient.takeSnapshot(
    repositoryName: String,
    snapshotName: String = formatTimestamp(), // sane default
    waitForCompletion: Boolean = false,
    timeout: Duration = 1.minutes
) = restClient.put {
    path("_snapshot", repositoryName, snapshotName)
    if (waitForCompletion) {
        parameter("wait_for_completion", true)
        parameter("timeout", "${timeout.inWholeSeconds}s")
    }
}.parseJsonObject()


suspend fun SearchClient.restoreSnapshot(
    repositoryName: String,
    snapshotName: String,
    waitForCompletion: Boolean = false,
    timeout: Duration = 1.minutes
) = restClient.post {
    path("_snapshot", repositoryName, snapshotName, "_restore")
    if (waitForCompletion) {
        parameter("wait_for_completion", true)
        parameter("timeout", "${timeout.inWholeSeconds}s")
    }
}.parseJsonObject()


suspend fun SearchClient.deleteSnapshot(
    repositoryName: String,
    snapshotName: String,
    waitForCompletion: Boolean = false,
    timeout: Duration = 1.minutes

) =
    restClient.delete {
        path("_snapshot", repositoryName, snapshotName)
        if (waitForCompletion) {
            parameter("wait_for_completion", true)
            parameter("timeout", "${timeout.inWholeSeconds}s")
        }
    }.parseJsonObject()


