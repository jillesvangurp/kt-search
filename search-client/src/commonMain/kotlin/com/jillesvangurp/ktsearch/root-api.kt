package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchEngineVersion(
    val distribution: String?=null,
    val number: String,
    @SerialName("build_flavor")
    val buildFlavor: String?,
    @SerialName("build_hash")
    val buildHash: String,
    @SerialName("build_date")
    val buildDate: String,
    @SerialName("build_snapshot")
    val buildSnapshot: Boolean,
    @SerialName("lucene_version")
    val luceneVersion: String,
    @SerialName("minimum_wire_compatibility_version")
    val minimumWireCompatibilityVersion: String,
    @SerialName("minimum_index_compatibility_version")
    val minimumIndexCompatibilityVersion: String,
)

@Serializable
data class SearchEngineInformation(
    val name: String,
    @SerialName("cluster_name")
    val clusterName: String,
    @SerialName("cluster_uuid")
    val clusterUUID: String,
    val version: SearchEngineVersion,
) {
    val variantInfo by lazy {
        VariantInfo(
            variant =
            when {
                // opensearch added the distribution
                this.version.distribution == "opensearch" && this.version.number.startsWith("1.") -> SearchEngineVariant.OS1
                this.version.distribution == "opensearch" && this.version.number.startsWith("2.") ->SearchEngineVariant.OS2
                this.version.distribution == "opensearch" && this.version.number.startsWith("3.") ->SearchEngineVariant.OS3
                this.version.number.startsWith("7.") -> SearchEngineVariant.ES7
                this.version.number.startsWith("8.") -> SearchEngineVariant.ES8
                this.version.number.startsWith("9.") -> SearchEngineVariant.ES9
                else -> error("version not recognized")
            },
            versionString = version.number
        )
    }
}


/**
 * Search engine variant meta data.
 *
 * You can get this from [SearchEngineInformation] to figure out which
 * search engine your client is connected to.
 */
@Suppress("unused")
data class VariantInfo(
    val variant: SearchEngineVariant,
    val versionString: String,
) {
    val majorVersion by lazy { versionString.split('.')[0].toIntOrNull() }
    val minorVersion by lazy { versionString.split('.')[1].toIntOrNull() }
    val patchVersion by lazy { versionString.split('.')[2].toIntOrNull() }
}

/**
 * Http GET to `/`
 *
 * Note, you can may want to use [SearchClient.engineInfo], which
 * caches the response and avoid calling this multiple times.
 *
 * @return meta information about the search engine
 */
suspend fun SearchClient.root(): SearchEngineInformation {
    return restClient.get {

    }.parse(SearchEngineInformation.serializer(), json)
}

