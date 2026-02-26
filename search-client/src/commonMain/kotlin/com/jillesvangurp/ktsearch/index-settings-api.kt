@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.mappingdsl.IndexSettings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Get index settings for [target] via `GET /{target}/_settings`.
 */
suspend fun SearchClient.getIndexSettings(target: String): JsonObject {
    return restClient.get {
        path(target, "_settings")
    }.parseJsonObject()
}

/**
 * Update index settings for [target] via `PUT /{target}/_settings`.
 *
 * Only non-null values are sent.
 */
suspend fun SearchClient.putIndexSettings(
    target: String,
    refreshInterval: String? = null,
    numberOfReplicas: String? = null,
    timeout: Duration? = null,
    masterTimeOut: Duration? = null,
): JsonObject {
    if (refreshInterval == null && numberOfReplicas == null) {
        return buildJsonObject {}
    }
    val settings = IndexSettings().apply {
        refreshInterval?.let { this["index.refresh_interval"] = it }
        numberOfReplicas?.let { this["index.number_of_replicas"] = it }
    }
    return putIndexSettings(
        target = target,
        settings = settings,
        timeout = timeout,
        masterTimeOut = masterTimeOut,
    )
}

/**
 * Update index settings for [target] using [settings].
 */
suspend fun SearchClient.putIndexSettings(
    target: String,
    settings: IndexSettings,
    timeout: Duration? = null,
    masterTimeOut: Duration? = null,
): JsonObject {
    return restClient.put {
        path(target, "_settings")
        parameter("timeout", timeout)
        parameter("master_timeout", masterTimeOut)
        json(settings)
    }.parseJsonObject()
}

private data class IndexSettingsSnapshot(
    val refreshInterval: String?,
    val numberOfReplicas: String?,
)

private fun JsonObject.findSetting(vararg path: String): String? {
    var current: JsonObject? = this
    for ((idx, key) in path.withIndex()) {
        val value = current?.get(key) ?: return null
        if (idx == path.lastIndex) {
            return value.jsonPrimitive.contentOrNull
        }
        current = value.jsonObject
    }
    return null
}

private suspend fun SearchClient.captureIndexSettingsSnapshots(
    target: String,
): Map<String, IndexSettingsSnapshot> {
    val response = getIndexSettings(target)
    return response.mapValues { (_, indexObj) ->
        val indexSettings = indexObj.jsonObject
        IndexSettingsSnapshot(
            refreshInterval = indexSettings.findSetting(
                "settings",
                "index",
                "refresh_interval",
            ),
            numberOfReplicas = indexSettings.findSetting(
                "settings",
                "index",
                "number_of_replicas",
            ),
        )
    }
}

/**
 * Temporarily optimize [target] for high-throughput indexing.
 *
 * If [disableRefreshInterval] is true, the index `refresh_interval` is set to `-1`.
 * If [setReplicasToZero] is true, `number_of_replicas` is set to `0`.
 * Both settings are restored in a `finally` block, and when refresh was disabled a
 * `refresh` call is executed after restoration.
 */
suspend fun <T> SearchClient.withTemporaryIndexingSettings(
    target: String,
    disableRefreshInterval: Boolean = false,
    setReplicasToZero: Boolean = false,
    block: suspend () -> T,
): T {
    if (!disableRefreshInterval && !setReplicasToZero) {
        return block()
    }
    require(target.isNotBlank()) {
        "target must be set when temporary indexing settings are enabled"
    }
    val snapshots = captureIndexSettingsSnapshots(target)
    snapshots.keys.forEach { index ->
        putIndexSettings(
            target = index,
            refreshInterval = if (disableRefreshInterval) "-1" else null,
            numberOfReplicas = if (setReplicasToZero) "0" else null,
            timeout = 5.seconds,
            masterTimeOut = 5.seconds,
        )
    }
    return try {
        block()
    } finally {
        try {
            snapshots.forEach { (index, snapshot) ->
                putIndexSettings(
                    target = index,
                    refreshInterval = if (disableRefreshInterval) {
                        snapshot.refreshInterval ?: "1s"
                    } else {
                        null
                    },
                    numberOfReplicas = if (setReplicasToZero) {
                        snapshot.numberOfReplicas ?: "1"
                    } else {
                        null
                    },
                    timeout = 5.seconds,
                    masterTimeOut = 5.seconds,
                )
            }
        } finally {
            if (disableRefreshInterval && snapshots.isNotEmpty()) {
                runCatching {
                    refresh(target = snapshots.keys.joinToString(","))
                }
            }
        }
    }
}
