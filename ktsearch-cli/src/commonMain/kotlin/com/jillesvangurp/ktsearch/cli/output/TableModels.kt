package com.jillesvangurp.ktsearch.cli.output

enum class OutputFormat {
    Table,
    Csv,
}

enum class TableStyle {
    Aligned,
}

data class TableData(
    val columns: List<String>,
    val rows: List<List<String>>,
)
