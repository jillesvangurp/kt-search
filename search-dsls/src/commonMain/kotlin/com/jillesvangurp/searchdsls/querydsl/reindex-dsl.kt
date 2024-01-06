package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.CustomValue
import com.jillesvangurp.jsondsl.JsonDsl

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

class ReindexSourceDSL : JsonDsl() {
    var index: String by property()
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
