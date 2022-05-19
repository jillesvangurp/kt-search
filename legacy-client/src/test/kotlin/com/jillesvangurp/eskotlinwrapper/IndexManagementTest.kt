package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.ObjectMapper
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions.Type
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.indexRepository
import org.elasticsearch.client.indices.GetMappingsRequest
import org.elasticsearch.xcontent.XContentType
import org.elasticsearch.xcontent.xContentBuilder
import org.junit.jupiter.api.Test

class IndexManagementTest : AbstractElasticSearchTest("indexmngmnt", createIndex = false) {
    @Test
    fun `should list aliases`() {
        repository.createIndex {
            source(this::class.java.getResource("/testmodel-settings.json").readText(), XContentType.JSON)
        }

        val writeAlias = "${repository.indexName}_write"
        val readAlias = "${repository.indexName}_read"

        esClient.indices()
            .updateAliases(
                IndicesAliasesRequest()
                    .addAliasAction(AliasActions(Type.ADD).index(repository.indexName).alias(writeAlias))
                    .addAliasAction(AliasActions(Type.ADD).index(repository.indexName).alias(readAlias)),
                RequestOptions.DEFAULT
            )

        val repository2 = esClient.indexRepository(
            index = repository.indexName,
            readAlias = readAlias,
            writeAlias = writeAlias,
            refreshAllowed = true,
            modelReaderAndWriter = JacksonModelReaderAndWriter(
                TestModel::class,
                ObjectMapper().findAndRegisterModules()
            )
        )
        val aliases = repository2.currentAliases()
        assertThat(aliases.size).isEqualTo(2)
        aliases.forEach {
            println(it.alias)
        }
    }

    @Test
    fun `create custom mapping`() {
        repository.createIndex {
            source(
                xContentBuilder(
                    mapOf(
                        "mappings" to mapOf(
                            "properties" to mapOf(
                                "title" to xContentBuilder(
                                    mapOf(
                                        "type" to "long"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
        esClient.indices().getMapping(GetMappingsRequest().indices(repository.indexName), RequestOptions.DEFAULT)
            .mappings().forEach { (f, m) ->
                println(f)
                println(m.source().string())
            }
    }
}
