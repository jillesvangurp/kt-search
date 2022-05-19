package recipesearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jillesvangurp.eskotlinwrapper.AsyncIndexRepository
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.matchAll
import com.jillesvangurp.searchdsls.querydsl.matchPhrase
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.search.configure
import org.elasticsearch.client.configure
import org.elasticsearch.client.healthAsync
import org.elasticsearch.cluster.health.ClusterHealthStatus
import java.io.File

class RecipeSearch(
    private val repository: AsyncIndexRepository<Recipe>,
    private val objectMapper: ObjectMapper
) {

    suspend fun healthStatus(): ClusterHealthStatus {
        return repository.client.cluster().healthAsync(ClusterHealthRequest()).status
    }

    suspend fun createNewIndex() {
        // BEGIN mapping_dsl
        repository.createIndex {
            configure {
                settings {
                    replicas = 0
                    shards = 1
                    // we have some syntactic sugar for adding custom analysis
                    // however we don't hava a complete DSL for this
                    // so we fall back to using put for things
                    // not in the DSL
                    addTokenizer("autocomplete") {
                        put("type", "edge_ngram")
                        put("min_gram", 2)
                        put("max_gram", 10)
                        put("token_chars", listOf("letter"))
                    }
                    addAnalyzer("autocomplete") {
                        put("tokenizer", "autocomplete")
                        put("filter", listOf("lowercase"))
                    }
                    addAnalyzer("autocomplete_search") {
                        put("tokenizer", "lowercase")
                    }
                }
                mappings {
                    text("allfields")
                    text("title") {
                        copyTo = listOf("allfields")
                        fields {
                            text("autocomplete") {
                                analyzer = "autocomplete"
                                searchAnalyzer = "autocomplete_search"
                            }
                        }
                    }
                    text("description") {
                        copyTo = listOf("allfields")
                    }
                    number<Int>("prep_time_min")
                    number<Int>("cook_time_min")
                    number<Int>("servings")
                    keyword("tags")
                    objField("author") {
                        text("name")
                        keyword("url")
                    }
                }
            }
        }
        // END mapping_dsl
    }

    suspend fun deleteIndex() {
        repository.deleteIndex()
    }

    // BEGIN index_recipes
    suspend fun indexExamples() {
        // use a small bulk size to illustrate how this can
        // work with potentially large amounts of files.
        repository.bulk(bulkSize = 3) {
            File("src/examples/resources/recipes")
                .listFiles { f -> f.extension == "json" }?.forEach {
                val parsed = objectMapper.readValue<Recipe>(it.readText())
                // lets use the sourceUrl as an id
                // use create=false to allow updates
                index(parsed.sourceUrl, parsed, create = false)
            }
        }
    }
    // END index_recipes

    // BEGIN search_recipes
    suspend fun search(text: String, start: Int, hits: Int):
        SearchResponse<Recipe> {
            return repository.search {
                configure {
                    from = start
                    resultSize = hits
                    query = if(text.isBlank()) {
                        matchAll()
                    } else {
                        bool {
                            should(
                                matchPhrase("title", text) {
                                    boost=2.0
                                },
                                match("title", text) {
                                    boost=1.5
                                    fuzziness="auto"
                                },
                                match("description", text)
                            )
                        }
                    }

                }
            }.toSearchResponse()
        }
    // END search_recipes

    // BEGIN autocomplete_recipes
    suspend fun autocomplete(text: String, start: Int, hits: Int):
        SearchResponse<Recipe> {
            return repository.search {
                configure {
                    from = start
                    resultSize = hits
                    query = if(text.isBlank()) {
                        matchAll()
                    } else {
                        match("title.autocomplete", text)
                    }

                }
            }.toSearchResponse()
        }
    // END autocomplete_recipes
}
