package com.jillesvangurp.jsondsl

/**
 * To keep this framework light weight, I decided to implement my own json serializer.
 * It's not that hard and it means that people can use whatever framework they want.
 */
class SimpleJsonSerializer: JsonDslSerializer {
    override fun serialize(properties: JsonDsl, pretty: Boolean): String {
        val buf = StringBuilder()
        write(buf = buf, indent = 0, indentStep = 2, pretty = pretty, obj = properties)
        return buf.toString()
    }

    private fun write(buf: StringBuilder, indent: Int, indentStep: Int = 2, pretty: Boolean, obj: Any?) {
        when(obj) {
            null -> buf.append("null")
            is RawJson -> buf.append(obj.value)
            is Number -> buf.append(obj.toString())
            is CharSequence -> {
                buf.append('"')
                obj.toString().escapeJson(buf)
                buf.append('"')
            }
            is Char -> {
                buf.append('"')
                obj.toString().escapeJson(buf)
                buf.append('"')
            }
            is Boolean -> buf.append(obj.toString())
            is Map<*,*> -> {
                buf.append("{")
                val iterator = obj.entries.filter { it.value != null }.iterator()
                while(iterator.hasNext()) {
                    val entry = iterator.next()
                    buf.newLine(indent + 1, indentStep, pretty)
                    buf.append('"')
                    entry.key.toString().escapeJson(buf)
                    buf.append("\":")
                    buf.space(pretty)
                    write(
                        buf = buf,
                        indent = indent + 1,
                        indentStep = indentStep,
                        pretty = pretty,
                        obj = entry.value
                    )
                    if(iterator.hasNext()) {
                        buf.append(',')
                    }

                }
                buf.newLine(indent,indentStep,pretty)
                buf.append("}")
            }
            is Iterable<*> -> {
                buf.append('[')
                val iterator=obj.iterator()
                while(iterator.hasNext()) {
                    val v = iterator.next()
                    buf.newLine(indent+1,indentStep,pretty)
                    write(buf = buf, indent = indent+1, indentStep = indentStep, pretty = pretty, obj = v)
                    if(iterator.hasNext()) {
                        buf.append(',')
                        buf.space(pretty)
                    }
                }
                buf.newLine(indent,indentStep,pretty)
                buf.append(']')
            }
            is Array<*> -> {
                buf.append('[')
                val iterator=obj.iterator()
                while(iterator.hasNext()) {
                    val v = iterator.next()
                    buf.newLine(indent+1,indentStep,pretty)
                    write(buf = buf, indent = indent+1, indentStep = indentStep, pretty = pretty, obj = v)
                    if(iterator.hasNext()) {
                        buf.append(',')
                        buf.space(pretty)
                    }
                }
                buf.newLine(indent,indentStep,pretty)
                buf.append(']')
            }
            else -> {
                // fallback to just treating everything else as a String
                buf.append('"')
                obj.toString().escapeJson(buf)
                buf.append('"')
            }
        }
    }

    private fun String.escapeJson(buf: StringBuilder): String {
        for(c in this) {
            when(c) {
                '\\' -> {
                    buf.append('\\')
                    buf.append(c)
                }
                '"' -> {
                    buf.append('\\')
                    buf.append(c)
                }
                '\b' -> {
                    buf.append('\\')
                    buf.append('b')
                }
                '\n' -> {
                    buf.append('\\')
                    buf.append('n')
                }
                '\t' -> {
                    buf.append('\\')
                    buf.append('t')
                }
                '\r' -> {
                    buf.append('\\')
                    buf.append('r')
                }
                else -> {
                    if(c<' ') {
                        val code = "000${c.code.toString(16)}"
                        buf.append("\\u${code.substring(code.length-4)}")
                    } else {
                        buf.append(c)
                    }
                }
            }
        }
        return this
    }
    private fun StringBuilder.newLine(indent: Int,indentStep:Int,pretty: Boolean) {
        if(pretty) {
            append('\n')
            append(" ".repeat(indent*indentStep))
        }
    }

    private fun StringBuilder.space(pretty: Boolean) {
        if(pretty) {
            append(' ')
        }
    }
}