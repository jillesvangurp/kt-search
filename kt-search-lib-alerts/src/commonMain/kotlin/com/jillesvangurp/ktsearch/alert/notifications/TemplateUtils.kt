package com.jillesvangurp.ktsearch.alert.notifications

internal fun renderTemplate(template: String, context: Map<String, String>): String {
    if (template.isEmpty()) return template
    val result = StringBuilder(template.length)
    var cursor = 0
    while (cursor < template.length) {
        val start = template.indexOf("{{", cursor)
        if (start == -1) {
            result.append(template.substring(cursor))
            break
        }
        result.append(template.substring(cursor, start))
        val end = template.indexOf("}}", start + 2)
        if (end == -1) {
            result.append(template.substring(start))
            break
        }
        val key = template.substring(start + 2, end).trim()
        val replacement = if (key.isEmpty()) {
            "[missing:]"
        } else {
            context[key] ?: "[missing:$key]"
        }
        result.append(replacement)
        cursor = end + 2
    }
    return result.toString()
}
