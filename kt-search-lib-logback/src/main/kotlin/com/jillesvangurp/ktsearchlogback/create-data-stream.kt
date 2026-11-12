package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import kotlin.time.Clock
import kotlin.time.Duration

/** Checks whether [name] exists, returning false for a missing resource. */
suspend fun SearchClient.dataStreamExists(name: String): Boolean {
    val response = restClient.get {
        path(name)
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
        println(response.getOrNull()?.text)
        true
    }
}

/** Provisions templates and a data stream, optionally configuring Elasticsearch ILM. */
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
    if(configureIlm) {
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
            put("index.lifecycle.name", "$prefix-ilm-policy")
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
