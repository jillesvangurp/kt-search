package com.jillesvangurp.mapbacked

import com.jillesvangurp.jsondsl.JsonDsl
import org.elasticsearch.xcontent.XContentBuilder
import org.elasticsearch.xcontent.XContentFactory
import org.elasticsearch.xcontent.writeAny
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

fun JsonDsl.toXContent(builder: XContentBuilder): XContentBuilder {
    builder.writeAny(this)
    return builder
}

fun JsonDsl.stringify(pretty: Boolean = false): String {
    val bos = ByteArrayOutputStream()
    val builder = XContentFactory.jsonBuilder(bos)
    if (pretty) {
        builder.prettyPrint()
    }
    toXContent(builder)
    builder.close()
    bos.flush()
    bos.close()
    return bos.toByteArray().toString(StandardCharsets.UTF_8)
}