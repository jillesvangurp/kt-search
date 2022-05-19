package org.elasticsearch.xcontent

import com.jillesvangurp.mapbacked.stringify
import com.jillesvangurp.jsondsl.JsonDslSerializer
import com.jillesvangurp.jsondsl.JsonDsl
import org.elasticsearch.xcontent.json.JsonXContent
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.reflect.KProperty

// Misc helpers to make dealing with XContent a bit less painful. There seems to be no way around this low level stuff
// in the Java client as lots of endpoints lack suitable type safe builders or schemas. Use with caution.

/**
 * Helper to stringify to json.
 *
 * @param pretty pretty print, defaults to false
 */
fun ToXContent.stringify(pretty: Boolean = false): String {
    val bos = ByteArrayOutputStream()
    val builder = XContentFactory.jsonBuilder(bos)
    if (pretty) {
        builder.prettyPrint()
    }
    toXContent(builder, ToXContent.EMPTY_PARAMS)
    builder.close()
    bos.flush()
    bos.close()
    return bos.toByteArray().toString(StandardCharsets.UTF_8)
}

/**
 * Helper to write json to an output stream. Set [pretty] to true to pretty print the json.
 */
fun ToXContent.stringify(out: OutputStream, pretty: Boolean = false) {
    val builder = XContentFactory.jsonBuilder(out)
    if (pretty) {
        builder.prettyPrint()
    }
    toXContent(builder, ToXContent.EMPTY_PARAMS)
    builder.close()
}

class XContentJsonDslSerializer: JsonDslSerializer {
    override fun serialize(properties: JsonDsl, pretty: Boolean): String {
        return properties.stringify(pretty)
    }
}

/**
 * Helper to write XContentBuilder to string. Note. if you want pretty printing, you need to enable
 * that on the builder before you start building. Also, this only works if the underlying
 * outputstream is a ByteArrayOutputStream.
 */
fun XContentBuilder.stringify(): String {
    if (this.outputStream is ByteArrayOutputStream) {
        this.flush()
        this.outputStream.flush()
        return (this.outputStream as ByteArrayOutputStream).toByteArray().toString(StandardCharsets.UTF_8)
    } else {
        throw IllegalStateException("Cannot grab content from underlying OutputStream because it is not a ByteArrayOutputStream")
    }
}

/**
 * Write any of the supported types as a value. Supports primitive and null values, Map, List, Sequence, Array, etc. You
 * can even have another XContentBuilder nested.
 */
fun XContentBuilder.writeAny(obj: Any?) {
    if (obj == null) {
        this.nullValue()
    } else when (obj) {
        is XContentBuilder -> {
            if (obj.outputStream is ByteArrayOutputStream) {
                obj.flush()
                obj.outputStream.flush()
                this.rawValue((obj.outputStream as ByteArrayOutputStream).toByteArray().inputStream(), XContentType.JSON)
            } else {
                // this only works with XContentBuilder implementations that use a ByteArrayOutputStream, like the one created by xContentBuilder
                throw IllegalStateException("Cannot grab content from underlying OutputStream because it is not a ByteArrayOutputStream")
            }
        }
        is Instant -> {
            this.value(obj.toString())
        }
        is Number -> {
            this.value(obj)
        }
        is String -> {
            this.value(obj)
        }
        is Boolean -> {
            this.value(obj)
        }
        is Map<*, *> -> {
            this.startObject()
            obj.entries.forEach { (k, v) ->
                field(k.toString())
                writeAny(v)
            }
            this.endObject()
        }
        is Array<*> -> {
            this.startArray()
            obj.forEach {
                this.writeAny(it)
            }
            this.endArray()
        }
        is Iterable<*> -> {
            this.startArray()
            obj.forEach {
                this.writeAny(it)
            }
            this.endArray()
        }
        is Enum<*> -> {
            this.value(obj.name)
        }
        is KProperty<*> -> this.value(obj.name)
        else -> {
            throw IllegalArgumentException("Unsupported type: ${obj::class}")
        }
    }
}

/**
 * Add an object using a block.
 */
fun XContentBuilder.obj(block: XContentBuilder.() -> Unit) {
    this.startObject()
    block.invoke(this)
    this.endObject()
}

/**
 * Add a field with [name] with an object as the value; use the [block] to customise the object.
 */
fun XContentBuilder.objField(name: String, block: XContentBuilder.() -> Unit) {
    this.startObject(name)
    block.invoke(this)
    this.endObject()
}

/**
 * Add a field with [name] with an array as the value; use the [block] to customise the array.
 */
fun XContentBuilder.arrField(name: String, block: XContentBuilder.() -> Unit) {
    this.startArray(name)
    block.invoke(this)
    this.endArray()
}

internal fun xContentBuilder(block: XContentBuilder.() -> Unit): XContentBuilder {
    val builder = JsonXContent.contentBuilder()
    block.invoke(builder)
    return builder
}

/**
 * Turn common objects like maps, lists, values, etc. into an XContentBuilder. Supports whatever is supported in [writeAny].
 */
fun xContentBuilder(obj: Any): XContentBuilder {
    return xContentBuilder {
        this.writeAny(obj)
    }
}
