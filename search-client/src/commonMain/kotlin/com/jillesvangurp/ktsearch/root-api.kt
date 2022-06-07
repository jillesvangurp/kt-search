package com.jillesvangurp.ktsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchEngineVersion(
    val distribution: String?,
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
                this.version.distribution != null -> SearchEngineVariant.OS1
                this.version.number.startsWith("7.") -> SearchEngineVariant.ES7
                this.version.number.startsWith("8.") -> SearchEngineVariant.ES8
                else -> error("version not recognized")
            },
            versionString = version.number
        )
    }
}

enum class SearchEngineVariant { ES7, ES8, OS1 }

data class VariantInfo(
    val variant: SearchEngineVariant,
    val versionString: String,
) {
    val majorVersion by lazy { versionString.split('.')[0].toIntOrNull() }
    val minorVersion by lazy { versionString.split('.')[1].toIntOrNull() }
    val patchVersion by lazy { versionString.split('.')[2].toIntOrNull() }
}

suspend fun SearchClient.searchEngineVersion(): SearchEngineInformation {
    return restClient.get {

    }.parse(SearchEngineInformation.serializer(), json)
}