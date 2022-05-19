package com.jillesvangurp.eskotlinwrapper.documentation.manual

import com.jillesvangurp.eskotlinwrapper.documentation.*
import com.jillesvangurp.kotlin4example.mdLink
import com.jillesvangurp.eskotlinwrapper.withTestIndex
import java.io.File

private const val recipeSearchSource = "src/examples/kotlin/recipesearch/RecipeSearch.kt"

val recipeSearchMd by withTestIndex<Thing, Lazy<String>>(index = "manual", refreshAllowed = true, createIndex = false) {
    sourceGitRepository.md {
        +"""
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
            ${mdLinkToRepoResource("here", "/src/examples/kotlin/recipesearch")}.

            ## Our data model
            
            Lets start with our data model and consider this simple example json file for chicken enchilladas:
            
        """
        mdCodeBlock(
            File("src/examples/resources/recipes/homemade-chicken-enchiladas.json").readText(),
            "json",
            allowLongLines = true
        )
        +"""
            We can create a simple Kotlin data model to represent recipes like this:
        """
        snippetFromSourceFile("src/examples/kotlin/recipesearch/Recipe.kt", "model classes")

        +"""
            Given this model, we can create simple `AsyncIndexRepository` and use it (see ${mdLink(
            manualPages["crudSupport"]!!.title,
            manualPages["crudSupport"]!!.fileName
        )}) 
            to create a simple ktor server that can index and search through recipes. 

            Lets start with our `main` function:
                
        """
        snippetFromSourceFile("src/examples/kotlin/recipesearch/ServerMain.kt", "main_function")

        +"""
            This creates an Elasticsearch client, a jackson object mapper, which we will use for serialization, 
            and an `AsyncIndexRepository`, which is version of the `IndexRepository` that can use co-routines. 

            These are injected into the `RecipeSearch` constructor. This class contains
            our business logic. Finally, we pass that to a function that constructs a simple asynchronous 
            KTor server (see code at the end of this article) to implement a simple REST api.
            
            ## Creating an index
            
            We create a custom index with the custom mapping DSL that is part of the Kotlin client.
        """
        snippetFromSourceFile(recipeSearchSource, "mapping_dsl")

        +"""
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
        """
        snippetFromSourceFile(recipeSearchSource, "index_recipes")

        +"""
            Note how small this code is. There's almost nothing to this. Yet this code is safe, robust, asynchronous,
            and it could trivially be modified to process many millions of documents. Simply set a larger bulk size 
            and iterate over a bigger data source. It doesn't matter where the data comes from. You could iterate
            over a database table, a CSV file, crawl the web, etc.
            
            # Searching
            
            Once we have documents in our index, we can search through them as follows:
        """
        snippetFromSourceFile(recipeSearchSource, "search_recipes")

        +"""
            As you can see, searching is similarly simple. The `search` extension function takes a block that
            allows you to customise a `SearchRequest`. Inside the block we set the `size` and `from` so we can 
            page through multiple pages of results.
            
            Then hardest part is adding a query. For this the client provides several options. In this case,
            we use Kotlin's `apply` extension function to make dealing with the Java builders in the `RestHighLevelClient`
            a bit more idiomatic. The advantage of this is that we don't have to chain the builder methods and gain some 
            compile time safety. We could also have opted to use a templated multi-line string as the source.
            
            Since returning the raw Elasticsearch Response is not very nice, we use our own response format and 
            convert object that Elasticsearch returns using an extension function.
        """
        snippetFromSourceFile("src/examples/kotlin/recipesearch/SearchResponse.kt", "search_response")

        +"""
            ## Simple Autocomplete
            
            Since we added custom analyzers on the `title.autocomplete` field, we can also implement that. The response 
            format for that is the same. Our mapping uses a simple edge ngram analyzer.
        """
        snippetFromSourceFile(recipeSearchSource, "autocomplete_recipes")

        +"""                
            ## Creating a Ktor server
            
            To expose the business logic via a simple REST service, we use KTor. Note that recent versions of
            Spring Boot also support co-routines so you may be able to adapt this example for use with that.
        """
        snippetFromSourceFile("src/examples/kotlin/recipesearch/ServerMain.kt", "ktor_setup")

        +"""
            KTor uses a DSL for defining the server. In this case, we simply reuse our Jackson object mapper
            to setup content negotiation and data conversion and then add a router with a few simple endpoints.
            
            Note that we use `withContext { ... }` to launch our suspending business logic. This suspends the ktor
            pipeline until the asynchronous stuff completes.
            
            ## Doing some requests
            
            To start the server, simply run `ServerMain` from your IDE and start Elasticsearch (e.g. by using the docker-compose file in the es_kibana directory).
            
            After it starts, you should be able to do some curl requests:
            
            ```
            ${'$'} curl -X DELETE localhost:8080/recipe_index
            ${'$'} curl -X POST localhost:8080/recipe_index
            ${'$'} curl -X POST localhost:8080/index_examples

            ${'$'} curl 'localhost:8080/search?q=banana'
            {"total_hits":1,"items":[{"title":"Banana Oatmeal Cookie","description":"This recipe has been handed down in my family for generations. It's a good way to use overripe bananas. It's also a moist cookie that travels well either in the mail or car.","ingredients":["1 1/2 cups sifted all-purpose flour","1/2 teaspoon baking soda","1 teaspoon salt","1/4 teaspoon ground nutmeg","3/4 teaspoon ground cinnamon","3/4 cup shortening","1 cup white sugar","1 egg","1 cup mashed bananas","1 3/4 cups quick cooking oats","1/2 cup chopped nuts"],"directions":["Preheat oven to 400 degrees F (200 degrees C).","Sift together the flour, baking soda, salt, nutmeg and cinnamon.","Cream together the shortening and sugar; beat until light and fluffy. Add egg, banana, oatmeal and nuts. Mix well.","Add dry ingredients, mix well and drop by the teaspoon on ungreased cookie sheet.","Bake at 400 degrees F (200 degrees C) for 15 minutes or until edges turn lightly brown. Cool on wire rack. Store in a closed container."],"prep_time_min":0,"cook_time_min":0,"servings":24,"tags":["dessert","fruit"],"author":{"name":"Blair Bunny","url":"http://allrecipes.com/cook/10179/profile.aspx"},"source_url":"http://allrecipes.com/Recipe/Banana-Oatmeal-Cookie/Detail.aspx"}]}
            ```
        """
    }
}
