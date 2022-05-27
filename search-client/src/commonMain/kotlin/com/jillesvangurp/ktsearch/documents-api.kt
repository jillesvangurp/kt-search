package com.jillesvangurp.ktsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class OperationType {
    Create,Index
}

//{"_index":"index-15952561278798383668","_type":"_doc","_id":"wfmrBYEB2kpMqSwAHKrd","_version":1,"result":"created",
// "_shards":{"total":2,"successful":1,"failed":0},"_seq_no":0,"_primary_term":1}

@kotlinx.serialization.Serializable
data class Shards(val total: Int, val successful: Int, val failed: Int)

@Serializable
data class DocumentCreateResponse(
    @SerialName("_index")
    val index: String,
    @SerialName("_type")
    val type: String,
    @SerialName("_id")
    val id: String,
    @SerialName("_version")
    val version: Long,
    val result: String,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("_seq_no")
    val seqNo: Int,
    @SerialName("_primary_term")
    val primaryTerm: Int
)

suspend fun SearchClient.createDocument(
    target: String,
    serializedJson: String,
    id: String? = null,
    ifSeqNo: Int? = null,
    ifPrimaryTerm: Int? = null,
    opType: OperationType? = null
): DocumentCreateResponse {
    return restClient.post {
        if(id ==null) {
            path(target, "_doc")
        } else {
            path(target, "_doc", id)
        }

        parameter("if_seq_no", ifSeqNo)
        parameter("if_primary_term", ifPrimaryTerm)
        parameter("op_type", opType?.name?.lowercase())

        rawBody(serializedJson)
    }.parse(DocumentCreateResponse.serializer(), json)
}

suspend fun SearchClient.deleteDocument(target: String, id: String) {
    restClient.delete {
        path(target,id)
    }
}

suspend fun SearchClient.getDocument(target: String, id: String) {
    restClient.get {
        path(target,"_doc",id)
    }
}
