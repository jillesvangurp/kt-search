package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.RestException
import com.jillesvangurp.ktsearch.get
import com.jillesvangurp.ktsearch.parseJsonObject
import com.jillesvangurp.ktsearch.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration

// Keep ISM ages relative to rollover, matching Elasticsearch ILM min_age.
internal fun logIsmPolicy(
    prefix: String,
    hotRollOverGb: Int,
    hotMaxAge: String,
    warmMinAge: Duration,
    deleteMinAge: Duration,
    warmSegments: Int,
) = withJsonDsl {
    this["policy"] = withJsonDsl {
        this["description"] = "Logback rollover and retention for $prefix"
        this["default_state"] = "hot"
        this["ism_template"] = listOf(withJsonDsl {
            this["index_patterns"] = listOf("$prefix*", ".ds-$prefix-*")
            this["priority"] = 300
        })
        this["states"] = listOf(
            withJsonDsl {
                this["name"] = "hot"
                this["actions"] = listOf(withJsonDsl {
                    this["rollover"] = withJsonDsl {
                        this["min_primary_shard_size"] = "${hotRollOverGb}gb"
                        this["min_index_age"] = hotMaxAge
                        this["prevent_empty_rollover"] = true
                    }
                })
                this["transitions"] = listOf(withJsonDsl {
                    this["state_name"] = "warm"
                    this["conditions"] = withJsonDsl {
                        this["min_rollover_age"] = "${warmMinAge.inWholeMilliseconds}ms"
                    }
                })
            },
            withJsonDsl {
                this["name"] = "warm"
                // ISM shrink creates a separate index outside the data stream.
                this["actions"] = listOf(withJsonDsl {
                    this["force_merge"] = withJsonDsl {
                        this["max_num_segments"] = warmSegments
                    }
                })
                this["transitions"] = listOf(withJsonDsl {
                    this["state_name"] = "delete"
                    this["conditions"] = withJsonDsl {
                        this["min_rollover_age"] = "${deleteMinAge.inWholeMilliseconds}ms"
                    }
                })
            },
            withJsonDsl {
                this["name"] = "delete"
                this["actions"] = listOf(withJsonDsl {
                    this["delete"] = withJsonDsl { }
                })
                this["transitions"] = emptyList<Any>()
            }
        )
    }
}

internal suspend fun SearchClient.configureLogIsmPolicy(
    prefix: String,
    hotRollOverGb: Int,
    hotMaxAge: String,
    warmMinAge: Duration,
    deleteMinAge: Duration,
    warmSegments: Int,
) {
    val policyId = "$prefix-ism-policy"
    val existing = restClient.get {
        path("_plugins", "_ism", "policies", policyId)
    }.fold(
        onSuccess = { json.parseToJsonElement(it.text).jsonObject },
        onFailure = {
            if (it is RestException && it.status == 404) null else throw it
        }
    )
    restClient.put {
        path("_plugins", "_ism", "policies", policyId)
        existing?.let {
            parameter("if_seq_no", it.getValue("_seq_no").jsonPrimitive.long)
            parameter("if_primary_term", it.getValue("_primary_term").jsonPrimitive.long)
        }
        json(logIsmPolicy(
            prefix, hotRollOverGb, hotMaxAge, warmMinAge,
            deleteMinAge, warmSegments
        ))
    }.parseJsonObject()
    log("configured ISM $policyId")
}
