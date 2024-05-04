package com.jillesvangurp.searchdsls.querydsl

fun SearchDSL.highlight(block: Highlight.() -> Unit) {
    val builder = Highlight()
    block.invoke(builder)
    this["highlight"] = builder
}

fun Highlight.field(name: String): Field {
    return Field(name)
}

fun Highlight.field(
    name: String,
    block: Field.() -> Unit,
): Field {
    val hf = Field(name)
    block.invoke(hf)
    return hf
}

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

open class Field(name: String) : ESQuery(name) {
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
    var order by queryDetails.property<Order>()
    var phraseLimit by queryDetails.property<Int>()
    var preTags by queryDetails.property<String>()
    var postTags by queryDetails.property<String>()
    var requireFieldMatch by queryDetails.property<Boolean>()
    var maxAnalyzedOffset by queryDetails.property<Int>()
    var tagsSchema by queryDetails.property<String>()
    var type by queryDetails.property<Type>()

    fun matchedFields(vararg matchedFields: String) = queryDetails.getOrCreateMutableList("matched_fields").also {
        it.addAll(matchedFields)
    }
}

class Highlight : Field("highlight") {
    fun fields(vararg fields: Field) =
        queryDetails.getOrCreateMutableList("fields")
            .addAll(fields.map { it.wrapWithName() })
}
