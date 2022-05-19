package recipesearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.jillesvangurp.eskotlinwrapper.JacksonModelReaderAndWriter
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.create
import org.elasticsearch.cluster.health.ClusterHealthStatus

// BEGIN main_function
suspend fun main(vararg args: String) {
    val objectMapper = ObjectMapper()
    // enable Kotlin integration and whatever else is on the classpath
    objectMapper.findAndRegisterModules()
    // make sure we convert names with underscores properly to and
    // from kotlin (camelCase)
    objectMapper.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE

    val esClient = create(host = "localhost", port = 9999)
    // shut down client cleanly after ktor exits
    esClient.use {
        val customSerde = JacksonModelReaderAndWriter(Recipe::class, objectMapper)
        val recipeRepository =
            esClient.asyncIndexRepository(
                index = "recipes",
                // we override the default because we want to reuse the objectMapper
                // and reuse our snake case setup
                modelReaderAndWriter = customSerde
            )
        val recipeSearch = RecipeSearch(recipeRepository, objectMapper)
        if (args.any { it == "-c" }) {
            // since recipe search does async stuff
            // we need a coroutine scope
            withContext(Dispatchers.IO) {
                // if you pass -c it bootstraps an index
                recipeSearch.deleteIndex()
                recipeSearch.createNewIndex()
                recipeSearch.indexExamples()
            }
        }

        // creates a simple ktor server
        createServer(objectMapper, recipeSearch).start(wait = true)
    }
}
// END main_function

// BEGIN ktor_setup
private fun createServer(
    objectMapper: ObjectMapper,
    recipeSearch: RecipeSearch
): NettyApplicationEngine {
    return embeddedServer(Netty, port = 8080) {
        // this will allow us to serialize data objects to json
        install(ContentNegotiation) {
            // lets reuse our mapper for this
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }

        routing {
            get("/") {
                call.respondText("Hello World!", ContentType.Text.Plain)
            }
            post("/recipe_index") {
                withContext(Dispatchers.IO) {
                    recipeSearch.createNewIndex()
                    call.respond(HttpStatusCode.Created)
                }
            }

            delete("/recipe_index") {
                withContext(Dispatchers.IO) {
                    recipeSearch.deleteIndex()
                    call.respond(HttpStatusCode.Gone)
                }
            }

            post("/index_examples") {
                withContext(Dispatchers.IO) {
                    recipeSearch.indexExamples()
                    call.respond(HttpStatusCode.Accepted)
                }
            }

            get("/health") {
                withContext(Dispatchers.IO) {

                    val healthStatus = recipeSearch.healthStatus()
                    if (healthStatus == ClusterHealthStatus.RED) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            "es cluster is $healthStatus"
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            "es cluster is $healthStatus"
                        )
                    }
                }
            }

            get("/search") {
                withContext(Dispatchers.IO) {

                    val params = call.request.queryParameters
                    val query = params["q"].orEmpty()
                    val from = params["from"]?.toInt() ?: 0
                    val size = params["size"]?.toInt() ?: 10

                    call.respond(recipeSearch.search(query, from, size))
                }
            }

            get("/autocomplete") {
                withContext(Dispatchers.IO) {
                    val params = call.request.queryParameters
                    val query = params["q"].orEmpty()
                    val from = params["from"]?.toInt() ?: 0
                    val size = params["size"]?.toInt() ?: 10

                    call.respond(recipeSearch.autocomplete(query, from, size))
                }
            }
        }
    }
}
// END ktor_setup
