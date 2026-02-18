package com.jillesvangurp.ktsearch.cli

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

actual fun platformFileExists(path: String): Boolean = File(path).exists()

actual fun platformIsInteractiveInput(): Boolean = System.console() != null

actual fun platformReadLineFromStdin(): String? = readLine()

actual fun platformCreateGzipWriter(path: String): NdjsonGzipWriter {
    return JvmNdjsonGzipWriter(path)
}

actual fun platformWriteUtf8File(path: String, content: String) {
    File(path).writeText(content, Charsets.UTF_8)
}

private class JvmNdjsonGzipWriter(path: String) : NdjsonGzipWriter {
    private val writer = BufferedWriter(
        OutputStreamWriter(
            GZIPOutputStream(FileOutputStream(path, false)),
            StandardCharsets.UTF_8,
        )
    )

    override fun writeLine(line: String) {
        writer.write(line)
        writer.newLine()
    }

    override fun close() {
        writer.close()
    }
}
