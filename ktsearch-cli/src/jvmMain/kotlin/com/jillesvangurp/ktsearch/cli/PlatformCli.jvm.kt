package com.jillesvangurp.ktsearch.cli

import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

actual fun platformFileExists(path: String): Boolean = File(path).exists()

actual fun platformIsInteractiveInput(): Boolean = System.console() != null

actual fun platformConsumeTopKey(): TopKey? {
    val input = System.`in`
    while (input.available() > 0) {
        val ch = input.read()
        when (ch) {
            'q'.code, 'Q'.code -> return TopKey.Quit
            'h'.code, 'H'.code, '?'.code -> return TopKey.Help
            27 -> return TopKey.Escape
        }
    }
    return null
}

actual fun platformEnableSingleKeyInput() {
}

actual fun platformDisableSingleKeyInput() {
}

actual fun platformReadLineFromStdin(): String? = readLine()

actual fun platformCreateGzipWriter(path: String): NdjsonGzipWriter {
    return JvmNdjsonGzipWriter(path)
}

actual fun platformCreateGzipReader(path: String): NdjsonGzipReader {
    return JvmNdjsonGzipReader(path)
}

actual fun platformReadUtf8File(path: String): String {
    return File(path).readText(Charsets.UTF_8)
}

actual fun platformWriteUtf8File(path: String, content: String) {
    File(path).writeText(content, Charsets.UTF_8)
}

actual fun platformGetEnv(name: String): String? = System.getenv(name)

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

private class JvmNdjsonGzipReader(path: String) : NdjsonGzipReader {
    private val reader = InputStreamReader(
        GZIPInputStream(FileInputStream(path)),
        StandardCharsets.UTF_8,
    ).buffered()

    override fun readLine(): String? {
        return reader.readLine()
    }

    override fun close() {
        reader.close()
    }
}
