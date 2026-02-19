package com.jillesvangurp.ktsearch.cli

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.gzip
import platform.posix.fileno
import platform.posix.isatty
import platform.posix.stdin

actual fun platformFileExists(path: String): Boolean =
    FileSystem.SYSTEM.exists(path.toPath())

@OptIn(ExperimentalForeignApi::class)
actual fun platformIsInteractiveInput(): Boolean = isatty(fileno(stdin)) != 0

actual fun platformReadLineFromStdin(): String? = readlnOrNull()

actual fun platformCreateGzipWriter(path: String): NdjsonGzipWriter {
    return NativeNdjsonGzipWriter(path)
}

actual fun platformCreateGzipReader(path: String): NdjsonGzipReader {
    return NativeNdjsonGzipReader(path)
}

actual fun platformReadUtf8File(path: String): String {
    return FileSystem.SYSTEM.read(path.toPath()) {
        readUtf8()
    }
}

actual fun platformWriteUtf8File(path: String, content: String) {
    FileSystem.SYSTEM.write(path.toPath(), mustCreate = false) {
        writeUtf8(content)
    }
}

private class NativeNdjsonGzipWriter(path: String) : NdjsonGzipWriter {
    private val sink = FileSystem.SYSTEM
        .sink(path.toPath(), mustCreate = false)
        .gzip()
        .buffer()

    override fun writeLine(line: String) {
        sink.writeUtf8(line)
        sink.writeByte('\n'.code)
    }

    override fun close() {
        sink.close()
    }
}

private class NativeNdjsonGzipReader(path: String) : NdjsonGzipReader {
    private val source = FileSystem.SYSTEM
        .source(path.toPath())
        .gzip()
        .buffer()

    override fun readLine(): String? {
        return source.readUtf8Line()
    }

    override fun close() {
        source.close()
    }
}
