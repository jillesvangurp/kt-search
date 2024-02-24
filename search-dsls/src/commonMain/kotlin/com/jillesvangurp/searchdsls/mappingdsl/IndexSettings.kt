@file:Suppress("unused")

package com.jillesvangurp.searchdsls.mappingdsl

import com.jillesvangurp.jsondsl.*
import kotlin.reflect.KProperty
import kotlin.time.Duration

@Suppress("LeakingThis")
class Analysis : JsonDsl() {
    class Analyzer : JsonDsl() {
        var type by property<String>()
        var tokenizer by property<String>()
        var filter by property<List<String>>()
        var charFilter by property<List<String>>()
        var positionIncrementGap by property<Int>()
    }

    class Normalizer : JsonDsl() {
        var type by property<String>()
        var filter by property<List<String>>()
        var charFilter by property<List<String>>()
    }

    open class Tokenizer : JsonDsl() {
        var type by property<String>()
    }

    open class CharFilter : JsonDsl() {
        var type by property<String>()

    }

    open class Filter : JsonDsl() {
        var type by property<String>()
    }

    private fun addConfig(type: String, name: String, json: JsonDsl) {
        val objects = this[type] as JsonDsl? ?: JsonDsl().also {
            this[type] = it
        }
        objects[name] = json
    }

    fun analyzer(name: String, block: Analyzer.() -> Unit) {
        addConfig("analyzer", name, Analyzer().apply(block))
    }

    fun normalizer(name: String, block: Normalizer.() -> Unit) {
        addConfig("normalizer", name, Normalizer().apply(block))
    }

    fun tokenizer(name: String, block: Tokenizer.() -> Unit) {
        addConfig("tokenizer", name, Tokenizer().apply(block))
    }

    fun charFilter(name: String, block: CharFilter.() -> Unit) {
        addConfig("char_filter", name, CharFilter().apply(block))
    }

    fun filter(name: String, block: Filter.() -> Unit) {
        addConfig("filter", name, Filter().apply(block))
    }

}

@JsonDslMarker
class IndexSettings : JsonDsl() {
    var replicas: Int by property("index.number_of_replicas")
    var shards: Int by property("index.number_of_shards")
    var refreshInterval: Duration by property("index.refresh_interval")
    var indexLifeCycleName: String by property("index.lifecycle.name")

    fun analysis(block: Analysis.() -> Unit) {
        this["analysis"] = Analysis().apply(block)
    }
}

@Suppress("LeakingThis")
@JsonDslMarker
open class FieldMappingConfig(typeName: String) : JsonDsl() {
    var type by property<String>()
    var boost by property<Double>()
    var store by property<Boolean>()
    var enabled by property<Boolean>()
    var copyTo by property<List<String>>()

    var analyzer by property<String>()
    var normalizer by property<String>()
    var searchAnalyzer by property<String>()

    var norms by property<Boolean>()
    var index by property<Boolean>()
    var splitQueriesOnWhitespace by property<Boolean>()
    var indexOptions by property<String>()
    var script by property<String>()
    var onScriptError by property<String>()
    var meta by property<Map<String, String>>()

    init {
        type = typeName
    }

    fun fields(block: FieldMappings.() -> Unit) {
        val fields = this["fields"] as FieldMappings? ?: FieldMappings()
        block.invoke(fields)
        this["fields"] = fields
    }
}

class KeywordFieldMappingConfig : FieldMappingConfig(typeName = "keyword") {
    var eagerGlobalOrdinals by property<Boolean>()
    var docValues by property<Boolean>()
    var ignoreAbove by property<String>()
    var nullValue by property<String>()
}

enum class KnnSimilarity {
    L2Norm,
    Cosine,
    DotProduct
}
@Suppress("MemberVisibilityCanBePrivate")
@JsonDslMarker
class FieldMappings : JsonDsl() {
    fun text(name: String) = field(name, "text") {}
    fun text(property: KProperty<*>) = field(property.name, "text") {}
    fun text(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "text", block)
    fun text(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) = field(property.name, "text", block)
    fun keyword(name: String) = keyword(name) {}
    fun keyword(property: KProperty<*>) = keyword(property.name) {}
    fun keyword(name: String, block: KeywordFieldMappingConfig.() -> Unit)  {
        val mapping = KeywordFieldMappingConfig()
        block.invoke(mapping)
        put(name, mapping, PropertyNamingConvention.AsIs)
    }
    fun keyword(property: KProperty<*>, block: KeywordFieldMappingConfig.() -> Unit) = keyword(property.name, block)
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
    fun geoPoint(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) =
        field(property.name, "geo_point", block)

    fun geoShape(name: String) = field(name, "geo_shape")
    fun geoShape(property: KProperty<*>) = field(property.name, "geo_shape")
    fun geoShape(name: String, block: FieldMappingConfig.() -> Unit) = field(name, "geo_shape", block)
    fun geoShape(property: KProperty<*>, block: FieldMappingConfig.() -> Unit) =
        field(property.name, "geo_shape", block)

    fun rankFeature(name: String, block: (FieldMappingConfig.() -> Unit)? = null) {
        field(name, "rank_feature", block)
    }
    fun rankFeature(property: KProperty<*>, block: (FieldMappingConfig.() -> Unit)? = null) =
        field(property.name, "rank_feature", block)
    fun rankFeatures(name: String, block: (FieldMappingConfig.() -> Unit)? = null) {
        field(name, "rank_features", block)
    }
    fun rankFeatures(property: KProperty<*>, block: (FieldMappingConfig.() -> Unit)? = null) =
        field(property.name, "rank_features", block)

    inline fun <reified T : Number> number(name: String) = number<T>(name) {}
    inline fun <reified T : Number> number(property: KProperty<*>) = number<T>(property.name) {}

    inline fun <reified T : Number> number(name: String, noinline block: FieldMappingConfig.() -> Unit) {
        val type = when (T::class) {
            Long::class -> "long"
            Int::class -> "integer"
            Short::class -> "short"
            Byte::class -> "byte"
            Float::class -> "float"
            Double::class -> "double"
            else -> throw IllegalArgumentException("unsupported type ${T::class}")
        }
        field(name, type, block)
    }

    inline fun <reified T : Number> number(property: KProperty<*>, noinline block: FieldMappingConfig.() -> Unit) =
        number<T>(property.name, block)

    fun objField(name: String, dynamic: String? = null, block: (FieldMappings.() -> Unit)? = null) {
        field(name, "object") {
            if (!dynamic.isNullOrBlank()) {
                this["dynamic"] = dynamic
            }
            if (block != null) {
                this["properties"] = FieldMappings().apply(block)
            }
        }
    }

    fun objField(property: KProperty<*>, dynamic: String? = null, block: (FieldMappings.() -> Unit)? = null) =
        objField(property.name, dynamic, block)

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

    fun denseVector(name: String, dimensions: Int, index: Boolean = false, similarity: KnnSimilarity=KnnSimilarity.Cosine,block: (FieldMappingConfig.() -> Unit)? = null) {
        field(name, "dense_vector") {
            this["dims"] = dimensions
            if(index) {
                this["index"] = index
                this["similarity"] = similarity.name.camelCase2SnakeCase()
            }
            block?.let { this.apply(it) }
        }
    }

    fun denseVector(property: KProperty<*>, dimensions: Int,index: Boolean = false, similarity: KnnSimilarity=KnnSimilarity.Cosine,block: (FieldMappingConfig.() -> Unit)? = null) =
        denseVector(property.name, dimensions,index,similarity,block)

    fun field(name: String, type: String) = field(name, type) {}
    fun field(property: KProperty<*>, type: String) = field(property.name, type) {}

    fun field(name: String, type: String, block: (FieldMappingConfig.() -> Unit)?) {
        val mapping = FieldMappingConfig(type)
        block?.invoke(mapping)
        put(name, mapping, PropertyNamingConvention.AsIs)
    }

    fun field(property: KProperty<*>, type: String, block: FieldMappingConfig.() -> Unit) =
        field(property.name, type, block)

    fun join(name: String, block: JoinDefinition.() -> Unit) {
        val join = JoinDefinition()
        join.apply(block)
        put(name, join, PropertyNamingConvention.AsIs)
    }
}

class JoinDefinition : JsonDsl() {
    init {
        put("type", "join")
    }

    fun relations(relations: Map<String, List<String>>) = put("relations", relations)
    fun relations(vararg relation: Pair<String, List<String>>) = put("relations", relation.toMap())
}

class DynamicTemplateDefinition : JsonDsl() {
    var matchMappingType by property<String>()
    var match by property<String>()
    var unmatch by property<String>()
    var pathMatch by property<String>()
    var pathUnmatch by property<String>()
    fun mapping(type: String, block: (FieldMappingConfig.() -> Unit)? = null) {
        put("mapping", FieldMappingConfig(type).also { config ->
            block?.let { config.apply(it) }
        })

    }
}

class IndexSettingsAndMappingsDSL(private val generateMetaFields: Boolean = false) :
    JsonDsl() {
    private var settings by property<IndexSettings>()
    private var mappings by property<JsonDsl>()

    fun settings(block: IndexSettings.() -> Unit) {
        val settingsMap = IndexSettings()
        block.invoke(settingsMap)

        settings = settingsMap
    }

    fun meta(block: JsonDsl.() -> Unit) {
        val newMeta = JsonDsl()
        block.invoke(newMeta)
        if (containsKey("mappings")) {
            mappings["_meta"] = newMeta
        } else {
            mappings = withJsonDsl  { this["_meta"] = newMeta }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun dynamicTemplate(dynamicTemplateId: String, block: DynamicTemplateDefinition.() -> Unit) {
        if (!containsKey("mappings")) {
            mappings = JsonDsl()
        }
        if (mappings["dynamic_templates"] == null) {
            mappings["dynamic_templates"] = mutableListOf<JsonDsl>()
        }
        mappings["dynamic_templates"]?.let { dynamicMappings ->
            dynamicMappings as MutableList<JsonDsl>
            dynamicMappings.add(withJsonDsl {
                this[dynamicTemplateId] = DynamicTemplateDefinition().apply(block)
            })
        }
    }

    fun mappings(dynamicEnabled: Boolean? = null, block: FieldMappings.() -> Unit) {
        val properties = FieldMappings()
        if (!containsKey("mappings")) {
            mappings = JsonDsl()
        }
        dynamicEnabled?.let {
            mappings["dynamic"] = dynamicEnabled.toString()
        }
        block.invoke(properties)
        mappings["properties"] = properties
    }
}