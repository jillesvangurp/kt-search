@file:Suppress("unused")

package com.jillesvangurp.searchdsls.mappingdsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.JsonDslMarker
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import kotlin.reflect.KProperty

class Analysis: JsonDsl() {
    class Analyzer : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
        var type by property<String>()
        var tokenizer by property<String>()
        var filter by property<List<String>>()
        var charFilter by property<List<String>>()
        var positionIncrementGap by property<Int>()
    }

    class Normalizer : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
        var type by property<String>()
        var filter by property<List<String>>()
        var charFilter by property<List<String>>()
    }
    open class Tokenizer : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
        var type by property<String>()
    }
    open class CharFilter : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
        var type by property<String>()

    }
    open class Filter : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
        var type by property<String>()
    }

    private fun addConfig(type: String, name: String, json: JsonDsl) {
        val objects = this[type] as JsonDsl? ?: JsonDsl().also {
            this[type] = it
        }
        objects[name] = json
    }

    fun analyzer(name: String, block: Analysis.Analyzer.() -> Unit) {
        addConfig("analyzer", name, Analysis.Analyzer().apply(block))
    }
    fun normalizer(name: String, block: Analysis.Normalizer.() -> Unit) {
        addConfig("normalizer", name, Analysis.Normalizer().apply(block))
    }

    fun tokenizer(name: String, block: Analysis.Tokenizer.() -> Unit) {
        addConfig("tokenizer", name, Analysis.Tokenizer().apply(block))
    }

    fun charFilter(name: String, block: Analysis.CharFilter.() -> Unit) {
        addConfig("char_filter", name, Analysis.CharFilter().apply(block))
    }

    fun filter(name: String, block: Analysis.Filter.() -> Unit) {
        addConfig("filter", name, Analysis.Filter().apply(block))
    }

}

@JsonDslMarker
class IndexSettings : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var replicas: Int by property("index.number_of_replicas")
    var shards: Int by property("index.number_of_shards")

    fun analysis(block : Analysis.()->Unit) {
        this["analysis"] = Analysis().apply(block)
    }
}

@JsonDslMarker
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

@Suppress("MemberVisibilityCanBePrivate")
@JsonDslMarker
class FieldMappings : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    fun text(name: String) = field(name, "text") {}
    fun text(property: KProperty<*>) = field(property.name, "text") {}
    fun text(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "text", block)
    fun text(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "text", block)
    fun keyword(name: String) = field(name, "keyword") {}
    fun keyword(property: KProperty<*>) = field(property.name, "keyword") {}
    fun keyword(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "keyword", block)
    fun keyword(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "keyword", block)
    fun bool(name: String) = field(name, "boolean") {}
    fun bool(property: KProperty<*>) = field(property.name, "boolean") {}
    fun bool(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "boolean", block)
    fun bool(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "boolean", block)
    fun date(name: String) = field(name, "date")
    fun date(property: KProperty<*>) = field(property.name, "date")
    fun date(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "date", block)
    fun date(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "date", block)

    fun geoPoint(name: String) = field(name, "geo_point")
    fun geoPoint(property: KProperty<*>) = field(property.name, "geo_point")
    fun geoPoint(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "geo_point", block)
    fun geoPoint(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "geo_point", block)

    fun geoShape(name: String) = field(name, "geo_shape")
    fun geoShape(property: KProperty<*>) = field(property.name, "geo_shape")
    fun geoShape(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "geo_shape", block)
    fun geoShape(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "geo_shape", block)

    inline fun <reified T : Number> number(name: String) = number<T>(name) {}
    inline fun <reified T : Number> number(property: KProperty<*>) = number<T>(property.name) {}

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
    inline fun <reified T : Number> number(property: KProperty<*>, noinline block: FieldMappingConfig.() -> Unit) = number<T>(property.name, block)

    fun objField(name: String, block: FieldMappings.() -> Unit) {
        field(name, "object") {
            val fieldMappings = FieldMappings()
            block.invoke(fieldMappings)
            if (fieldMappings.size > 0) {
                this["properties"] = fieldMappings
            }
        }
    }
    fun objField(property: KProperty<*>, block: FieldMappings.() -> Unit) = objField(property.name, block)

    fun nestedField(name: String, block: FieldMappings.() -> Unit) {
        field(name, "nested") {
            val fieldMappings = FieldMappings()
            block.invoke(fieldMappings)
            if (fieldMappings.size > 0) {
                this["properties"] = fieldMappings
            }
        }
    }
    fun nestedField(property: KProperty<*>, block: FieldMappings.() -> Unit) = nestedField(property.name, block)

    fun field(name: String, type: String) = field(name, type) {}
    fun field(property: KProperty<*>, type: String) = field(property.name, type) {}

    fun field(name: String, type: String, block: FieldMappingConfig.() -> Unit) {
        val mapping = FieldMappingConfig(type)
        block.invoke(mapping)
        put(name, mapping, PropertyNamingConvention.AsIs)
    }
    fun field(property: KProperty<*>, type: String, block: FieldMappingConfig.() -> Unit) = field(property.name, type, block)
}

class IndexSettingsAndMappingsDSL (private val generateMetaFields: Boolean=false) : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    private var settings by property<IndexSettings>()
    private var mappings by property<JsonDsl>()
//    private var dynamicEnabled by property<Boolean>(customPropertyName = "dynamic")

    fun settings(block: IndexSettings.() -> Unit) {
        val settingsMap = IndexSettings()
        block.invoke(settingsMap)

        settings = settingsMap
    }

    fun meta(block: JsonDsl.() -> Unit) {
        val newMeta = JsonDsl()
        block.invoke(newMeta)
        if(containsKey("mappings")) {
            mappings["_meta"] = newMeta
        } else {
            mappings=JsonDsl().apply { this["_meta"] = newMeta }
        }
    }

    fun mappings(dynamicEnabled: Boolean? = null, block: FieldMappings.() -> Unit) {
        val properties = FieldMappings()
        if(!containsKey("mappings")) {
            mappings = JsonDsl()
        }
        dynamicEnabled?.let {
            mappings["dynamic"] = dynamicEnabled
        }
        block.invoke(properties)
        mappings["properties"] = properties
    }
}