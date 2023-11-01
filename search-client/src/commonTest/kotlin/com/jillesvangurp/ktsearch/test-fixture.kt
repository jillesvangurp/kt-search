package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.random.Random

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TestDocument(
    val name: String,
    val description: String? = null,
    val number: Long? = null,
    val tags: List<String>? = null,
    val point: List<Double>? = null,
    val id : Long = Random.nextLong(),
    @EncodeDefault // default to same time so tests depending on document equality don't fail
    val timestamp: Instant = Instant.fromEpochMilliseconds(6666666666),
    val feature: Int = 42
) {
    companion object {
        val mapping = IndexSettingsAndMappingsDSL().apply {
            mappings(dynamicEnabled = false) {
                number<Long>(TestDocument::id)
                text(TestDocument::name)
                text(TestDocument::description)
                number<Long>(TestDocument::number)
                keyword(TestDocument::tags)
                geoPoint(TestDocument::point)
                date(TestDocument::timestamp)
                rankFeature(TestDocument::feature)
            }
        }
    }

    fun json(pretty: Boolean = false): String {
        return if (pretty)
            DEFAULT_PRETTY_JSON.encodeToString(serializer(), this)
        else
            DEFAULT_JSON.encodeToString(serializer(), this)
    }
}

