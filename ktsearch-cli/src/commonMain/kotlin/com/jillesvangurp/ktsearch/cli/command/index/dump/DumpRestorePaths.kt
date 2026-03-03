package com.jillesvangurp.ktsearch.cli.command.index.dump

internal fun parentDir(path: String): String {
    val slash = path.lastIndexOf('/')
    val backslash = path.lastIndexOf('\\')
    val separatorIndex = maxOf(slash, backslash)
    return if (separatorIndex < 0) "." else path.substring(0, separatorIndex)
}

internal fun joinPath(dir: String, name: String): String {
    if (dir == "." || dir.isBlank()) {
        return name
    }
    val separator = if (dir.contains('\\') && !dir.contains('/')) "\\" else "/"
    return if (dir.endsWith('/') || dir.endsWith('\\')) {
        "$dir$name"
    } else {
        "$dir$separator$name"
    }
}
