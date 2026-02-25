package com.jillesvangurp.ktsearch.cli.output

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class OutputOptions : OptionGroup(name = "Output") {
    private val csv by option(
        "--csv",
        help = "Render output as CSV.",
    ).flag(default = false)

    val outputFormat: OutputFormat
        get() = if (csv) OutputFormat.Csv else OutputFormat.Table
}
