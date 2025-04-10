package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.CustomValue
import com.jillesvangurp.jsondsl.JsonDsl
import kotlin.time.Duration

enum class Conflict(override val value: String) : CustomValue<String> {
    ABORT("abort"),
    PROCEED("proceed");
}

class ReindexDSL : JsonDsl() {
    var conflicts: Conflict by property()
    var maxDocs: Int by property(customPropertyName = "max_docs")

    fun source(block: ReindexSourceDSL.() -> Unit) {
        val sourceDSL = ReindexSourceDSL()
        block(sourceDSL)
        this["source"] = sourceDSL
    }

    fun destination(block: ReindexDestinationDSL.() -> Unit) {
        val destinationDSL = ReindexDestinationDSL()
        block(destinationDSL)
        this["dest"] = destinationDSL
    }

    fun script(block: ReindexScriptDSL.() -> Unit) {
        val scriptDSL = ReindexScriptDSL()
        block(scriptDSL)
        this["script"] = scriptDSL
    }
}

class ReindexSourceDSL : JsonDsl(), QueryClauses {
    var index: String by property()
    var batchSize: Int by property("size")

    var query: ESQuery
        get() {
            val map = this["query"] as Map<*, *>
            val (name, queryDetails) = map.entries.first()
            if(queryDetails is JsonDsl) {
                return ESQuery(name.toString(), queryDetails)
            } else {
                error("wrong type for queryDetails, should be JsonDSL")
            }
        }
        set(value) {
            this["query"] = value.wrapWithName()
        }

    fun fields(vararg names: String) {
        if (names.isNotEmpty()) {
            this["_source"] = names.toList()
        }
    }
    fun fields(names: Collection<String>) {
        if (names.isNotEmpty()) {
            this["_source"] = names
        }
    }

    fun remote(block: ReindexRemoteDSL.() -> Unit) {
        val scriptDSL = ReindexRemoteDSL()
        block(scriptDSL)
        this["remote"] = scriptDSL
    }

    fun slice(block: ReindexSliceDSL.() -> Unit) {
        val scriptDSL = ReindexSliceDSL()
        block(scriptDSL)
        this["slice"] = scriptDSL
    }
}

class ReindexRemoteDSL : JsonDsl() {
    var host: String by property()
    var username: String by property()
    var password: String by property()
    var socketTimeout: Duration by property(customPropertyName = "socket_timeout")
    var connectTimeout: Duration by property(customPropertyName = "connect_timeout")
    var headers: Map<String, String> by property()
}

class ReindexSliceDSL : JsonDsl() {
    var id: Int by property()
    var max: Int by property()
}

class ReindexDestinationDSL : JsonDsl() {
    var index: String by property()
    var versionType: ReindexVersionType by property(customPropertyName = "version_type")
    var operationType: ReindexOperationType by property(customPropertyName = "op_type")
    var pipeline: String by property()
}

enum class ReindexVersionType(override val value: String) : CustomValue<String> {
    INTERNAL("internal"),
    EXTERNAL("external"),
    EXTERNAL_GT("external_gt"),
    EXTERNAL_GTE("external_gte")
}

enum class ReindexOperationType(override val value: String) : CustomValue<String> {
    INDEX("index"),
    CREATE("create")
}

enum class Language(override val value: String) : CustomValue<String> {
    PAINLESS("painless"),
    EXPRESSION("expression"),
    MUSTACHE("mustache"),
    JAVA("java")
}

class ReindexScriptDSL : JsonDsl() {
    var source: String by property()
    var language: Language by property(customPropertyName = "lang")
}
