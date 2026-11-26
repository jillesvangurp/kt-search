package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration

/** Checks whether data stream [name] exists, returning false for a regular index. */
suspend fun SearchClient.dataStreamExists(name: String): Boolean {
    val response = restClient.get {
        path("_data_stream", name)
    }
    val e = response.exceptionOrNull()
    return if(e!=null) {
        if(e is RestException) {
            if(e.status == 404) {
                false
            } else {
                throw e
            }
        } else {
            throw e
        }
    } else {
        // OpenSearch can return 200 with an empty list for a regular index.
        val streams = json.parseToJsonElement(response.getOrThrow().text)
            .jsonObject.getValue("data_streams").jsonArray
        streams.any { it.jsonObject["name"]?.jsonPrimitive?.content == name }
    }
}

/**
 * Provisions templates and a data stream. [configureIlm] enables Elasticsearch
 * ILM or OpenSearch 3 ISM, detected from the cluster. Ages are measured from
 * rollover. [warmShrinkShards] applies only to Elasticsearch: OpenSearch shrink
 * does not preserve data stream membership, so its warm state only force merges.
 * Disabling lifecycle configuration leaves existing backing indexes unchanged.
 */
suspend fun SearchClient.manageDataStream(
    prefix: String,
    hotRollOverGb:Int,
    hotMaxAge:String,
    numberOfReplicas: Int,
    numberOfShards: Int,
    warmMinAge: Duration,
    deleteMinAge: Duration,
    warmShrinkShards: Int,
    warmSegments: Int,
    configureIlm: Boolean,
): Boolean {
    val openSearch = engineInfo().version.distribution == "opensearch"
    if (configureIlm && openSearch) {
        require(engineInfo().version.number.startsWith("3.")) {
            "Logback lifecycle provisioning requires OpenSearch 3 or Elasticsearch"
        }
        configureLogIsmPolicy(
            prefix, hotRollOverGb, hotMaxAge, warmMinAge,
            deleteMinAge, warmSegments
        )
    } else if (configureIlm) {
        setIlmPolicy("$prefix-ilm-policy") {
            hot {
                actions {
                    this["rollover"] = withJsonDsl {
                        this["max_primary_shard_size"] = "${hotRollOverGb}gb"
                        this["max_age"] = hotMaxAge
                    }
                }
            }
            warm {
                minAge(warmMinAge)
                actions {
                    shrink(warmShrinkShards)
                    forceMerge(warmSegments)
                }
            }
            delete {
                minAge(deleteMinAge)
                actions {
                    delete()
                }
            }
        }.let {
            log("configured ILM $prefix-ilm-policy: ${it.acknowledged}")
        }
    }
    // using component templates is a good idea
    updateComponentTemplate("$prefix-template-settings") {
        settings {
            replicas = numberOfReplicas
            shards = numberOfShards
            if (configureIlm && !openSearch) {
                put("index.lifecycle.name", "$prefix-ilm-policy")
            }
        }
        meta {
            put("created_by","kt-search-lib-logback")
            put("created_at", Clock.System.now().toString())
        }
    }.let {
        log("configured $prefix-template-settings")
    }
    updateComponentTemplate("$prefix-template-mappings") {
        dynamicTemplate("keywords") {
            matchMappingType = "string"
            match = "*"
            // this works on the mdc and context fields where set turn dynamic to true
            mapping("keyword") {
                this["ignore_above"]="256"
            }
        }

        mappings(false) {
            text("text")
            text(LogMessage::message) {
                fields {
                    keyword("keyword") {
                        ignoreAbove="256"
                    }
                }
                copyTo = listOf("text")
            }
            date("@timestamp")
            keyword(LogMessage::thread)
            keyword(LogMessage::level)
            keyword(LogMessage::logger) {
                copyTo = listOf("text")
            }
            keyword(LogMessage::contextName)
            objField(LogMessage::mdc, dynamic = "true") {
            }
            objField(LogMessage::context, dynamic = "true") {
            }
            objField(LogMessage::exceptionList) {
                keyword(LogException::className) {
                    ignoreAbove="256"
                    copyTo = listOf("text")
                }
                text(LogMessage::message) {
                    copyTo = listOf("text")
                }
            }
        }
        meta {
            put("created_by","kt-search-lib-logback")
            put("created_at", Clock.System.now().toString())
        }
    }.let {
        log("configured $prefix-template-mappings")
    }
    // now create the template
    createIndexTemplate("$prefix-template") {
        indexPatterns = listOf("$prefix*")
        // make sure to specify an empty object for data_stream
        dataStream = withJsonDsl {
            // the elastic docs are a bit vague on what goes here
        }
        // make sure we outrank elastics own stuff
        priority=300
        composedOf = listOf("$prefix-template-settings", "$prefix-template-mappings")
    }.let {
        log("configured $prefix-template")
    }
    // create the data stream
    if(!dataStreamExists(prefix)) {
        createDataStream(prefix)
        log("created data_stream $prefix")

    }
    return true
}
