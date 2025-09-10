package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.querydsl.ESQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject

@Suppress("unused")
class AliasAction: JsonDsl() {
    var alias by property<String>()
    var aliases by property<List<String>>()
    var index by property<String>()
    var indices by property<List<String>>()
    var filter by property<ESQuery>()
}

/**
 * DSL for configuring the update aliases request. The request is a list of actions (add, remove, remove_index).
 * Each action is configured with an [AliasAction] that specifies the affected indices, aliases, etc.
 */
class AliasUpdateRequest: JsonDsl() {
    private var actions by property("actions",mutableListOf<JsonDsl>())

    /**
     * Configure an add action.
     */
    fun add(block: AliasAction.()->Unit) {
        actions.add(withJsonDsl {
            this["add"] =AliasAction().apply(block)
        })
    }

    /**
     * Configure a remove action.
     */
    fun remove(block: AliasAction.()->Unit) {
        actions.add(withJsonDsl {
            this["remove"] =AliasAction().apply(block)
        })
    }

    /**
     * Configure a remove_index action.
     */
    fun removeIndex(block: AliasAction.()->Unit) {
        actions.add(withJsonDsl {
            this["remove_index"] =AliasAction().apply(block)
        })
    }

    /**
     * Adds an alias for an index. Shorter alternative for configuring the add operation.
     */
    fun addAliasForIndex(index: String, alias: String) {
        add {
            this.index = index
            this.alias = alias
        }
    }

    /**
     * Removes the index from an alias and optionally deletes the index. Shorter alternative for configuring the remove and removeIndex operations.
     */
    fun removeAliasForIndex(index: String, alias: String, deleteIndex: Boolean = false) {
        if(deleteIndex) {
            // this is mutually exclusive with removing the alias, you can't do both in a single update
            removeIndex {
                this.index = index
            }
        } else {
            remove {
                this.index = index
                this.alias = alias
            }
        }
    }
}

/**
 * Atomically process alias updates with a DSL.
 */
suspend fun SearchClient.updateAliases(block: AliasUpdateRequest.()->Unit): AcknowledgedResponse {
    return restClient.post {
        path("_aliases")
        body = AliasUpdateRequest().apply(block).json(true)
    }.parse(AcknowledgedResponse.serializer())
}


@Serializable
data class AliasResponse(val aliases: Map<String,JsonObject>)

/**
 * Returns the aliases for a target.
 *
 * The Elasticresponse for this is somewhat hard to model because it is a bit open ended. If all you need is the set of alias names for a string,
 * use the more user friendly [getAliasNamesForIndex] or [getIndexesForAlias] to find out with indices an alias points to. Or pick apart the response manually.
 */
suspend fun SearchClient.getAliases(target: String?=null): Map<String, AliasResponse> {
    return restClient.get {
        path(target,"_alias")
    }.parse(MapSerializer(String.serializer(),AliasResponse.serializer()))
}

/**
 * Returns the set of alias names for an index or an empty set.
 *
 * If you need the full `_alias` API, use [getAliases]. This is intended to be a more user friendly way to use that.
 */
suspend fun SearchClient.getAliasNamesForIndex(index: String): Set<String> {
    return try {
        getAliases(index)[index]?.aliases?.keys.orEmpty()
    } catch (e: RestException) {
        if(e.status == 404) emptySet() else throw e
    }
}

/**
 * Returns the set of indices that have the alias. If you call this on an index, the returned set will just contain the index.
 *
 * If you need the full `_alias` API, use [getAliases]. This is intended to be a more user friendly way to use that.
 */
suspend fun SearchClient.getIndexesForAlias(alias: String): Set<String> {
    return try {
        getAliases(alias).keys
    } catch (e: RestException) {
        if(e.status == 404) emptySet() else throw e
    }
}