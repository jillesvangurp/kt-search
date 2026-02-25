package com.jillesvangurp.ktsearch.cli.output

object JsonOutputRenderer {
    fun renderTableOrRaw(
        rawJson: String,
        outputFormat: OutputFormat,
        preferredColumns: List<String>? = null,
    ): String {
        val table = JsonTableAdapter.fromJson(
            rawJson = rawJson,
            preferredColumns = preferredColumns,
        ) ?: JsonTableAdapter.fromJsonObject(rawJson)
        return if (table != null) {
            TableRenderer.render(table, outputFormat)
        } else {
            rawJson
        }
    }
}
