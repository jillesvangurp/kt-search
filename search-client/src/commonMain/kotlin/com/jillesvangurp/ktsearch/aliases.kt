package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.querydsl.ESQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject

@Suppress("unused")
class AliasAction: JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var alias by property<String>()
    var aliases by property<List<String>>()
    var index by property<String>()
    var indices by property<List<String>>()
    var filter by property<ESQuery>()
}

class AliasUpdateRequest: JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    private var actions by property(mutableListOf<JsonDsl>())
    fun add(block: AliasAction.()->Unit) {
        actions.add(withJsonDsl {
            this["add"] =AliasAction().apply(block)
        })
    }
    fun remove(block: AliasAction.()->Unit) {
        actions.add(withJsonDsl {
            this["remove"] =AliasAction().apply(block)
        })
    }
    fun removeIndex(block: AliasAction.()->Unit) {
        actions.add(withJsonDsl {
            this["remove_index"] =AliasAction().apply(block)
        })
    }
}

suspend fun SearchClient.updateAliases(block: AliasUpdateRequest.()->Unit): AcknowledgedResponse {
    return restClient.post {
        path("_aliases")
        body = AliasUpdateRequest().apply(block).json(true)
    }.parse(AcknowledgedResponse.serializer())
}


@Serializable
data class AliasResponse(val aliases: Map<String,JsonObject>)
suspend fun SearchClient.getAliases(target: String?=null): Map<String, AliasResponse> {
    return restClient.get {
        path(target,"_alias")
    }.parse(MapSerializer(String.serializer(),AliasResponse.serializer()))
}