package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.EnumValue
import com.jillesvangurp.jsondsl.JsonDsl

enum class Conflict(override val value: String) : EnumValue<String> {
    ABORT("abort"),
    PROCEED("proceed");
}

class ReindexDSL : JsonDsl() {
    var conflicts by property<Conflict>()
    var maxDocs by property<String>(customPropertyName = "max_docs")

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
}

class ReindexSourceDSL : JsonDsl() {
    var index: String by property()
}

class ReindexDestinationDSL : JsonDsl() {
    var index: String by property()
}
