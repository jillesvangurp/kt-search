package com.jillesvangurp.ktsearch.petstore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * Raw pet document stored in the primary `pets` index.
 */
data class Pet(
    val id: String,
    val name: String,
    val animal: String,
    val breed: String,
    val sex: String,
    val age: Int,
    val price: Double,
    val description: String,
    val traits: List<String> = emptyList(),
    val imageUrl: String? = null
)

@Serializable
/**
 * Enriched view written to the `pet-search` index.
 */
data class PetSearchDocument(
    val id: String,
    val name: String,
    val animal: String,
    val breed: String,
    val sex: String,
    val age: Int,
    val price: Double,
    val priceBucket: String,
    val description: String,
    val traits: List<String> = emptyList(),
    val wikipediaUrl: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null
)

@Serializable
/**
 * Aggregation summary for driving the UI filters.
 */
data class SearchFacets(
    val animals: Map<String, Long>,
    val breeds: Map<String, Long>,
    val sexes: Map<String, Long>,
    val ageRanges: Map<String, Long>,
    val priceRanges: Map<String, Long>
)

@Serializable
data class PetSearchResult(
    /** The enriched pet returned by the search index. */
    val pet: PetSearchDocument,
    /** Highlight snippets keyed by field name; used by the UI cards. */
    val highlights: Map<String, List<String>> = emptyMap()
)

@Serializable
data class PetSearchResponse(
    /** Total number of hits reported by Elasticsearch (can be track_total_hits aware). */
    val total: Long,
    /** End-to-end search time as reported by Elasticsearch. */
    val tookMs: Long,
    /** Search hits mapped to the UI model. */
    val results: List<PetSearchResult>,
    /** Aggregation buckets that back the facet dropdowns. */
    val facets: SearchFacets
)
