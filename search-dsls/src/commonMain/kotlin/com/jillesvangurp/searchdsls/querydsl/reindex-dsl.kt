package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.EnumValue
import com.jillesvangurp.jsondsl.JsonDsl

enum class Conflict(override val value: String) : EnumValue<String> {
    ABORT("abort"),
    PROCEED("proceed");
}

class ReindexDSL : JsonDsl() {
    var conflicts: Conflict by property()
    var maxDocs: String by property(customPropertyName = "max_docs")

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
}

enum class Language(override val value: String) : EnumValue<String> {
    PAINLESS("painless"),
    EXPRESSION("expression"),
    MUSTACHE("mustache"),
    JAVA("java")
}

class ReindexScriptDSL : JsonDsl() {
    var source: String by property()
    var language: Language by property(customPropertyName = "lang")
}
