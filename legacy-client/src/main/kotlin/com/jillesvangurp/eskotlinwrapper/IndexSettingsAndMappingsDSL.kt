@file:Suppress("unused")

package com.jillesvangurp.eskotlinwrapper

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.JsonDSL
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import com.jillesvangurp.jsondsl.json
import org.elasticsearch.xcontent.*

import org.elasticsearch.xcontent.XContentBuilder
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

@JsonDSL
class IndexSettings : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var replicas: Int by property("index.number_of_replicas")
    var shards: Int by property("index.number_of_shards")

    private fun indexObject(type: String, name: String, block: JsonDsl.() -> Unit) {
        val analysis = get("analysis") as JsonDsl? ?: JsonDsl()
        val objects = analysis[type] as JsonDsl? ?: JsonDsl()
        val objectProperties = JsonDsl()
        block.invoke(objectProperties)
        objects[name] = objectProperties
        analysis[type] = objects
        put("analysis", analysis)
    }

    fun addAnalyzer(name: String, block: JsonDsl.() -> Unit) {
        indexObject("analyzer", name, block)
    }

    fun addTokenizer(name: String, block: JsonDsl.() -> Unit) {
        indexObject("tokenizer", name, block)
    }

    fun addCharFilter(name: String, block: JsonDsl.() -> Unit) {
        indexObject("char_filter", name, block)
    }

    fun addFilter(name: String, block: JsonDsl.() -> Unit) {
        indexObject("filter", name, block)
    }
}

@JsonDSL
class FieldMappingConfig(typeName: String) : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var type: String by property()
    var boost by property<Double>()
    var docValues by property<Boolean>()
    var store by property<Boolean>()
    var enabled by property<Boolean>()
    var copyTo: List<String> by property()

    var analyzer: String by property()
    var searchAnalyzer: String by property()

    init {
        type = typeName
    }

    fun fields(block: FieldMappings.() -> Unit) {
        val fields = this["fields"] as FieldMappings? ?: FieldMappings()
        block.invoke(fields)
        this["fields"] = fields
    }
}

@JsonDSL
class FieldMappings : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    fun text(name: String) = field(name, "text") {}
    fun text(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "text", block)
    fun keyword(name: String) = field(name, "keyword") {}
    fun keyword(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "keyword", block)
    fun bool(name: String) = field(name, "boolean") {}
    fun bool(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "boolean", block)
    fun date(name: String) = field(name, "date")
    fun date(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "date", block)

    fun geoPoint(name: String) = field(name, "geo_point")
    fun geoPoint(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "geo_point", block)

    fun geoShape(name: String) = field(name, "geo_shape")
    fun geoShape(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "geo_shape", block)

    inline fun <reified T : Number> number(name: String) = number<T>(name) {}

    inline fun <reified T : Number> number(name: String, noinline block: FieldMappingConfig.() -> Unit) {
        val type = when (T::class) {
            Long::class -> "long"
            Int::class -> "integer"
            Float::class -> "float"
            Double::class -> "double"
            else -> throw IllegalArgumentException("unsupported type ${T::class} explicitly specify type")
        }
        field(name, type, block)
    }

    fun objField(name: String, block: FieldMappings.() -> Unit) {
        field(name, "object") {
            val fieldMappings = FieldMappings()
            block.invoke(fieldMappings)
            if (fieldMappings.size > 0) {
                this["properties"] = fieldMappings
            }
        }
    }

    fun nestedField(name: String, block: FieldMappings.() -> Unit) {
        field(name, "nested") {
            val fieldMappings = FieldMappings()
            block.invoke(fieldMappings)
            if (fieldMappings.size > 0) {
                this["properties"] = fieldMappings
            }
        }
    }

    fun field(name: String, type: String) = field(name, type) {}

    fun field(name: String, type: String, block: FieldMappingConfig.() -> Unit) {
        val mapping = FieldMappingConfig(type)
        block.invoke(mapping)
        put(name, mapping, PropertyNamingConvention.AsIs)
    }

    fun stringify(pretty: Boolean = false):String {
        return xContentBuilder {
            if (pretty) {
                this.prettyPrint()
            }
            writeAny(this)
        }.stringify()
    }

    internal fun build(pretty: Boolean = false): XContentBuilder {
        val mappings = this
        return xContentBuilder {
            if (pretty) prettyPrint()
            obj {
                field("properties")
                writeAny(mappings)
            }
        }
    }
}

class IndexSettingsAndMappingsDSL private constructor(private val generateMetaFields: Boolean) {
    private var settings: IndexSettings? = null
    private var meta: JsonDsl? = null
    private var mappings: FieldMappings? = null
    private var dynamicEnabled: Boolean? = null

    fun settings(block: IndexSettings.() -> Unit) {
        val settingsMap = IndexSettings()
        block.invoke(settingsMap)

        settings = settingsMap
    }

    fun meta(block: JsonDsl.() -> Unit) {
        if (meta == null) meta = JsonDsl()
        block.invoke(meta!!)
    }

    fun mappings(dynamicEnabled: Boolean? = null, block: FieldMappings.() -> Unit) {
        this.dynamicEnabled = dynamicEnabled
        if (mappings == null) mappings = FieldMappings()
        block.invoke(mappings!!)
    }

    internal fun build(pretty: Boolean = false): XContentBuilder {
        if (generateMetaFields) {
            // if it did not exist, create it.
            if (meta == null) meta = object : JsonDsl() {}
            val mappingJson = mappings?.json(true) ?: "{}"
            meta!!["content_hash"] =
                Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(mappingJson.toByteArray()))
            meta!!["timestamp"] = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        }

        return xContentBuilder {
            if (pretty) prettyPrint()
            obj {
                if (settings != null) {
                    field("settings")
                    writeAny(settings)
                }
                if (mappings != null || meta != null) {
                    objField("mappings") {
                        if (meta != null) {
                            field("_meta")
                            writeAny(meta)
                        }
                        if (dynamicEnabled != null) {
                            field("dynamic")
                            writeAny(dynamicEnabled)
                        }
                        if (mappings != null) {
                            field("properties")
                            writeAny(mappings)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun indexSettingsAndMappings(
            generateMetaFields: Boolean = true,
            pretty: Boolean = false,
            block: IndexSettingsAndMappingsDSL.() -> Unit
        ): XContentBuilder {
            val settingsAndMappings = IndexSettingsAndMappingsDSL(generateMetaFields)
            block.invoke(settingsAndMappings)
            return settingsAndMappings.build(pretty)
        }
    }
}