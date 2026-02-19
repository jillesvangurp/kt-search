package com.jillesvangurp.ktsearch.cli

private fun unsupported(feature: String): Nothing =
    error("wasmJs CLI does not support $feature yet")

actual fun platformFileExists(path: String): Boolean = false

actual fun platformIsInteractiveInput(): Boolean = false

actual fun platformReadLineFromStdin(): String? = null

actual fun platformCreateGzipWriter(path: String): NdjsonGzipWriter {
    return WasmNdjsonGzipWriter(path)
}

actual fun platformCreateGzipReader(path: String): NdjsonGzipReader {
    return WasmNdjsonGzipReader(path)
}

actual fun platformReadUtf8File(path: String): String {
    unsupported("reading files")
}

actual fun platformWriteUtf8File(path: String, content: String) {
    unsupported("writing files")
}

private class WasmNdjsonGzipWriter(private val path: String) : NdjsonGzipWriter {
    override fun writeLine(line: String) {
        unsupported("gzip write for '$path'")
    }

    override fun close() = Unit
}

private class WasmNdjsonGzipReader(private val path: String) : NdjsonGzipReader {
    override fun readLine(): String? {
        unsupported("gzip read for '$path'")
    }

    override fun close() = Unit
}
