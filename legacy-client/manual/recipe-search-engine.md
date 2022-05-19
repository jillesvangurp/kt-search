[previous](dsl-customization.md) | [index](index.md) | [next](about.md)

___

# Example: Building a Recipe Search Engine 

The Elasticsearch Kotlin Client is designed to simplify writing code that
interacts with Elasticsearch.

The easiest way to demonstrate how it works is just showing it with a simple 
example. The code below is very loosely based on an example in the 
[Elastic examples repository](https://github.com/elastic/examples/tree/master/Search/recipe_search_java) 
and borrows data from that project.

In this article, we will create a simple [KTor](https://ktor.io/servers/index.html) server that 
implements a simple Rest service for indexing and searching recipes. Since both ktor and this library
support co-routines, we'll do the extra bit of work to fully utilize that.

You can find the source code for this example 
[here](https://github.com/jillesvangurp/es-kotlin-wrapper-client/src/examples/kotlin/recipesearch).

## Our data model

Lets start with our data model and consider this simple example json file for chicken enchilladas:

```json
{
	"title": "Homemade Chicken Enchiladas",
	"description": "These enchiladas are great. Even my 5 year old loves them!",
	"ingredients": [
		"1 tablespoon olive oil",
		"2 cooked chicken breasts, shredded",
		"1 onion, diced",
		"1 green bell pepper, diced",
		"1 1/2 cloves garlic, chopped",
		"1 cup cream cheese",
		"1 cup shredded Monterey Jack cheese",
		"1 (15 ounce) can tomato sauce",
		"1 tablespoon chili powder",
		"1 tablespoon dried parsley",
		"1 teaspoon dried oregano",
		"1/2 teaspoon salt",
		"1/2 teaspoon ground black pepper",
		"8 (10 inch) flour tortillas",
		"2 cups enchilada sauce",
		"1 cup shredded Monterey Jack cheese"
	],
	"directions": [
		"Preheat oven to 350 degrees F (175 degrees C).",
		"Heat olive oil in a skillet over medium heat. Cook and stir chicken, onion, green bell pepper, garlic, cream cheese, and 1 cup Monterey Jack cheese in hot oil until the cheese melts, about 5 minutes. Stir tomato sauce, chili powder, parsley, oregano, salt, and black pepper into the chicken mixture.",
		"Divide mixture evenly into tortillas, roll the tortillas around the filling, and arrange in a baking dish. Cover with enchilada sauce and remaining 1 cup Monterey Jack cheese.",
		"Bake in preheated oven until cheese topping melts and begins to brown, about 15 minutes."
	],
	"prep_time_min": 15,
	"cook_time_min": 20,
	"servings": 8,
	"tags": [ "main dish" ],
	"author": {
		"name": "Mary Kate",
		"url": "http://allrecipes.com/cook/14977239/profile.aspx"
	},
	"source_url": "http://allrecipes.com/Recipe/Homemade-Chicken-Enchiladas/Detail.aspx"
}

```

We can create a simple Kotlin data model to represent recipes like this:

```kotlin
data class Author(val name: String, val url: String)

data class Recipe(
  val title: String,
  val description: String,
  val ingredients: List<String>,
  val directions: List<String>,
  val prepTimeMin: Int,
  val cookTimeMin: Int,
  val servings: Int,
  val tags: List<String>,
  val author: Author,
  // we will use this as our ID as well
  val sourceUrl: String
)
```

Given this model, we can create simple `AsyncIndexRepository` and use it (see [Using the IndexRepository](crud-support.md)) 
to create a simple ktor server that can index and search through recipes. 

Lets start with our `main` function:

```kotlin
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
```

This creates an Elasticsearch client, a jackson object mapper, which we will use for serialization, 
and an `AsyncIndexRepository`, which is version of the `IndexRepository` that can use co-routines. 

These are injected into the `RecipeSearch` constructor. This class contains
our business logic. Finally, we pass that to a function that constructs a simple asynchronous 
KTor server (see code at the end of this article) to implement a simple REST api.

## Creating an index

We create a custom index with the custom mapping DSL that is part of the Kotlin client.

```kotlin
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
```

This somewhat elaborate mapping example shows how you can mix our DSL with simple put
calls on the underlying `MutableMap`. The DSL provides some support for commonly used things
but since Elasticsearch has so many custom things, it's not feasible to manually map all of
that to the DSL. For unmapped things, you can simply use put with primitives, maps, lists, etc.

If you prefer, you can also use `source` to inject raw json from either a string or an InputStream,
or attempt to use the very limited builder that comes with the RestHighLevelClient.

## Indexing using the Bulk DSL

To index recipe documents, we use a simple function that uses the
bulk DSL to bulk index all the files in the `src/examples/resources/recipes` directory. Bulk indexing 
allows Elasticsearch to process batches of documents efficiently.

```kotlin
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
```

Note how small this code is. There's almost nothing to this. Yet this code is safe, robust, asynchronous,
and it could trivially be modified to process many millions of documents. Simply set a larger bulk size 
and iterate over a bigger data source. It doesn't matter where the data comes from. You could iterate
over a database table, a CSV file, crawl the web, etc.

# Searching

Once we have documents in our index, we can search through them as follows:

```kotlin
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
```

As you can see, searching is similarly simple. The `search` extension function takes a block that
allows you to customise a `SearchRequest`. Inside the block we set the `size` and `from` so we can 
page through multiple pages of results.

Then hardest part is adding a query. For this the client provides several options. In this case,
we use Kotlin's `apply` extension function to make dealing with the Java builders in the `RestHighLevelClient`
a bit more idiomatic. The advantage of this is that we don't have to chain the builder methods and gain some 
compile time safety. We could also have opted to use a templated multi-line string as the source.

Since returning the raw Elasticsearch Response is not very nice, we use our own response format and 
convert object that Elasticsearch returns using an extension function.

```kotlin
data class SearchResponse<T : Any>(val totalHits: Long, val items: List<T>)

suspend fun <T : Any> AsyncSearchResults<T>
.toSearchResponse(): SearchResponse<T> {
  val collectedHits = mutableListOf<T>()
  this.mappedHits.collect {
    collectedHits.add(it)
  }
  return SearchResponse(this.total, collectedHits)
}
```

## Simple Autocomplete

Since we added custom analyzers on the `title.autocomplete` field, we can also implement that. The response 
format for that is the same. Our mapping uses a simple edge ngram analyzer.

```kotlin
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
```

## Creating a Ktor server

To expose the business logic via a simple REST service, we use KTor. Note that recent versions of
Spring Boot also support co-routines so you may be able to adapt this example for use with that.

```kotlin
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
```

KTor uses a DSL for defining the server. In this case, we simply reuse our Jackson object mapper
to setup content negotiation and data conversion and then add a router with a few simple endpoints.

Note that we use `withContext { ... }` to launch our suspending business logic. This suspends the ktor
pipeline until the asynchronous stuff completes.

## Doing some requests

To start the server, simply run `ServerMain` from your IDE and start Elasticsearch (e.g. by using the docker-compose file in the es_kibana directory).

After it starts, you should be able to do some curl requests:

```
$ curl -X DELETE localhost:8080/recipe_index
$ curl -X POST localhost:8080/recipe_index
$ curl -X POST localhost:8080/index_examples

$ curl 'localhost:8080/search?q=banana'
{"total_hits":1,"items":[{"title":"Banana Oatmeal Cookie","description":"This recipe has been handed down in my family for generations. It's a good way to use overripe bananas. It's also a moist cookie that travels well either in the mail or car.","ingredients":["1 1/2 cups sifted all-purpose flour","1/2 teaspoon baking soda","1 teaspoon salt","1/4 teaspoon ground nutmeg","3/4 teaspoon ground cinnamon","3/4 cup shortening","1 cup white sugar","1 egg","1 cup mashed bananas","1 3/4 cups quick cooking oats","1/2 cup chopped nuts"],"directions":["Preheat oven to 400 degrees F (200 degrees C).","Sift together the flour, baking soda, salt, nutmeg and cinnamon.","Cream together the shortening and sugar; beat until light and fluffy. Add egg, banana, oatmeal and nuts. Mix well.","Add dry ingredients, mix well and drop by the teaspoon on ungreased cookie sheet.","Bake at 400 degrees F (200 degrees C) for 15 minutes or until edges turn lightly brown. Cool on wire rack. Store in a closed container."],"prep_time_min":0,"cook_time_min":0,"servings":24,"tags":["dessert","fruit"],"author":{"name":"Blair Bunny","url":"http://allrecipes.com/cook/10179/profile.aspx"},"source_url":"http://allrecipes.com/Recipe/Banana-Oatmeal-Cookie/Detail.aspx"}]}
```


___

[previous](dsl-customization.md) | [index](index.md) | [next](about.md)

