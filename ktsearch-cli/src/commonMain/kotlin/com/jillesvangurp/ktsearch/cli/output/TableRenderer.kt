package com.jillesvangurp.ktsearch.cli.output

object TableRenderer {
    fun render(
        tableData: TableData,
        outputFormat: OutputFormat,
        tableStyle: TableStyle = TableStyle.Aligned,
    ): String {
        return when (outputFormat) {
            OutputFormat.Table -> renderTable(tableData, tableStyle)
            OutputFormat.Csv -> renderCsv(tableData)
        }
    }

    fun renderTable(
        tableData: TableData,
        tableStyle: TableStyle = TableStyle.Aligned,
    ): String {
        if (tableData.columns.isEmpty()) {
            return ""
        }
        return when (tableStyle) {
            TableStyle.Aligned -> renderAligned(tableData)
        }
    }

    fun renderCsv(tableData: TableData): String {
        if (tableData.columns.isEmpty()) {
            return ""
        }
        val lines = mutableListOf<String>()
        lines.add(renderCsvRow(tableData.columns))
        tableData.rows.forEach { row ->
            lines.add(renderCsvRow(row, tableData.columns.size))
        }
        return lines.joinToString("\n")
    }

    private fun renderAligned(tableData: TableData): String {
        val widths = tableData.columns.mapIndexed { colIndex, column ->
            val maxRowValue = tableData.rows.maxOfOrNull { row ->
                row.getOrNull(colIndex)?.length ?: 0
            } ?: 0
            maxOf(column.length, maxRowValue)
        }
        val header = renderPaddedRow(tableData.columns, widths)
        val separator = widths.joinToString("  ") { "-".repeat(it) }
        if (tableData.rows.isEmpty()) {
            return listOf(header, separator).joinToString("\n")
        }
        val body = tableData.rows.map { row ->
            renderPaddedRow(row, widths)
        }
        return listOf(header, separator).plus(body).joinToString("\n")
    }

    private fun renderPaddedRow(values: List<String>, widths: List<Int>): String {
        return widths.indices.joinToString("  ") { index ->
            val value = values.getOrNull(index).orEmpty()
            value.padEnd(widths[index])
        }
    }

    private fun renderCsvRow(values: List<String>, expectedColumns: Int? = null): String {
        val valuesToRender = if (expectedColumns == null) {
            values
        } else {
            (0 until expectedColumns).map { values.getOrNull(it).orEmpty() }
        }
        return valuesToRender.joinToString(",") { escapeCsv(it) }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        if (!needsQuotes) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
