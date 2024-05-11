package com.jillesvangurp.searchdsls.querydsl

import kotlin.reflect.KProperty

@Suppress("EnumEntryName")
enum class BoundaryScanner {
    chars,
    sentence,
    word,
}

@Suppress("EnumEntryName")
enum class Encoder {
    default,
    html,
}

@Suppress("EnumEntryName")
enum class Fragmenter {
    simple,
    span,
}

@Suppress("EnumEntryName")
enum class Order {
    none,
    score,
}

@Suppress("EnumEntryName")
enum class Type {
    unified,
    plain,
    fvh,
}

open class HighlightField(name: String) : ESQuery(name) {
    constructor(property: KProperty<*>) : this(property.name)

    var boundaryChars by queryDetails.property<String>()
    var boundaryMaxScan by queryDetails.property<Int>()
    var boundaryScanner by queryDetails.property<BoundaryScanner>()
    var boundaryScannerLocale by queryDetails.property<String>()
    var encoder by queryDetails.property<Encoder>()
    var fragmenter by queryDetails.property("fragmenter",Fragmenter.span)
    var fragmentOffset by queryDetails.property<Int>()
    var fragmentSize by queryDetails.property<Int>()

    var highlightQuery by queryDetails.esQueryProperty()

    var noMatchSize by queryDetails.property<Int>()
    var numberOfFragments by queryDetails.property<Int>()
    var phraseLimit by queryDetails.property<Int>()
    var maxAnalyzedOffset by queryDetails.property<Int>()
    var tagsSchema by queryDetails.property<String>()
    var type by queryDetails.property<Type>()

    fun matchedFields(vararg matchedFields: String) = queryDetails.getOrCreateMutableList("matched_fields").also {
        it.addAll(matchedFields)
    }
}

class Highlight : ESQuery("highlight") {
    var preTags by queryDetails.property<String>()
    var postTags by queryDetails.property<String>()
    var requireFieldMatch by queryDetails.property<Boolean>()
    var order by queryDetails.property<Order>()

    fun fields(vararg fields: HighlightField) =
        queryDetails.getOrCreateMutableList("fields")
            .addAll(fields.map { it.wrapWithName() })

    fun add(
        name: String,
        block: (HighlightField.() -> Unit)? = null,
    ) {
        val hf = HighlightField(name)
        block?.invoke(hf)
        fields(hf)
    }
    fun add(
        name: KProperty<*>,
        block: (HighlightField.() -> Unit)? = null,
    ) {
        val hf = HighlightField(name)
        block?.invoke(hf)
        fields(hf)
    }
}

fun SearchDSL.highlight(
    vararg fields: HighlightField,
    block: (Highlight.() -> Unit)?=null
) {
    val builder = Highlight()
    if(fields.isNotEmpty()) {
        builder.fields(*fields)
    }
    block?.invoke(builder)
    this["highlight"] = builder
}