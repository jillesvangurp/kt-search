package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl

class ReindexDSL : JsonDsl() {
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
