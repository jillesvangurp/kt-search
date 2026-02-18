package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.cli.output.JsonTableAdapter
import com.jillesvangurp.ktsearch.cli.output.OutputFormat
import com.jillesvangurp.ktsearch.cli.output.TableData
import com.jillesvangurp.ktsearch.cli.output.TableRenderer
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TableOutputTest {
    @Test
    fun rendersAlignedTable() {
        val table = TableData(
            columns = listOf("status", "index"),
            rows = listOf(
                listOf("green", "products"),
                listOf("yellow", "inventory"),
            ),
        )

        val rendered = TableRenderer.render(table, OutputFormat.Table)

        rendered.contains("status") shouldBe true
        rendered.contains("products") shouldBe true
    }

    @Test
    fun rendersCsvAndEscapesValues() {
        val table = TableData(
            columns = listOf("a", "b"),
            rows = listOf(listOf("hello,world", "\"quoted\"")),
        )

        val rendered = TableRenderer.render(table, OutputFormat.Csv)

        rendered shouldBe "a,b\n\"hello,world\",\"\"\"quoted\"\"\""
    }

    @Test
    fun adaptsJsonArrayToTable() {
        val raw = """
            [
              {"status":"green","index":"a"},
              {"status":"yellow","index":"b"}
            ]
        """.trimIndent()

        val table = JsonTableAdapter.fromJson(raw)

        table?.columns shouldBe listOf("status", "index")
        table?.rows shouldBe listOf(
            listOf("green", "a"),
            listOf("yellow", "b"),
        )
    }

    @Test
    fun adapterReturnsNullForNonArrayJson() {
        JsonTableAdapter.fromJson("{\"status\":\"green\"}") shouldBe null
    }
}
