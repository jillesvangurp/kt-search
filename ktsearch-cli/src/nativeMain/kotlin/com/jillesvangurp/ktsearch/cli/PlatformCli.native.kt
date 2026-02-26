package com.jillesvangurp.ktsearch.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.gzip
import platform.posix.fileno
import platform.posix.isatty
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.POLLIN
import platform.posix.read
import platform.posix.stdin
import platform.posix.system

actual fun platformFileExists(path: String): Boolean =
    FileSystem.SYSTEM.exists(path.toPath())

@OptIn(ExperimentalForeignApi::class)
actual fun platformIsInteractiveInput(): Boolean = isatty(fileno(stdin)) != 0

private var singleKeyEnabled: Boolean = false

@OptIn(ExperimentalForeignApi::class)
actual fun platformEnableSingleKeyInput() {
    if (singleKeyEnabled) {
        return
    }
    val exitCode = system(
        "stty -echo -icanon min 0 time 0 < /dev/tty > /dev/tty 2>/dev/null"
    )
    if (exitCode == 0) {
        singleKeyEnabled = true
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformDisableSingleKeyInput() {
    if (!singleKeyEnabled) {
        return
    }
    system("stty sane < /dev/tty > /dev/tty 2>/dev/null")
    singleKeyEnabled = false
}

@OptIn(ExperimentalForeignApi::class)
actual fun platformConsumeTopKey(): TopKey? = memScoped {
    val fd = fileno(stdin)
    val pollFd = alloc<pollfd>()
    val oneByte = ByteArray(1)
    pollFd.fd = fd
    pollFd.events = POLLIN.convert()
    pollFd.revents = 0
    val ready = poll(pollFd.ptr, 1.convert(), 0)
    if (ready <= 0 || (pollFd.revents.toInt() and POLLIN) == 0) {
        return@memScoped null
    }
    val readCount: Long = oneByte.usePinned { pinned ->
        read(fd, pinned.addressOf(0), 1.convert())
    }
    if (readCount <= 0) {
        return@memScoped null
    }
    when (oneByte[0].toInt()) {
        'q'.code, 'Q'.code -> TopKey.Quit
        'h'.code, 'H'.code, '?'.code -> TopKey.Help
        27 -> TopKey.Escape
        else -> null
    }
}

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
