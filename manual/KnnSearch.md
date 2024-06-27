# KNN Search 

| [KT Search Manual](README.md) | Previous: [Creating Data Streams](DataStreams.md) | Next: [Extending the Json DSLs](ExtendingTheDSL.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

An exciting new feature in Elasticsearch is KNN search, aka. vector search or semantic search.

And kt-search has you covered and makes this as easy as possible.

Conceptually, vector search is very simple:

1. You use some AI model to produce so-called embeddings (vectors). 
These vectors encode the learned semantics of your data.
1. You index the embeddings using the "dense_vector" field type
1. You use the same AI model to generate a vector for whatever users query on
1. And you let Elasticsearch figure out the "nearest" vector match.

The devil is of course in the details. You can use off-the shelf AI models from e.g. OpenAI. But these 
models have their limitations. And training your own models is also possible but can be a lot of work.
        
The following example implements a simple knn search using some pre-calculated embeddings.
The embeddings were generated with openai using their text-similarity-ada-001 model.

This is of course not the most advanced model available. However, we are constrained here by the maximum vector length
that elasticsearch allows here of 1024. Some of the more advanced models in openai have a dimensionality 
(vector length) of multiple tens of thousands. These presumably capture more semantic information.

```kotlin

data class Embeddings(val id: String, val embedding: List<Double>)

// load the pre-calculated embeddings from a tsv file
val embeddings = Thread.currentThread()
  .contextClassLoader.getResourceAsStream("embeddings.tsv")
  ?.let { stream ->
    csvReader {
      delimiter = '\t'
    }.readAllWithHeader(stream)
  }?.map {
    Embeddings(
      it["id"]!!,
      it["embedding"]?.let { value ->
        DEFAULT_JSON.decodeFromString(
          ListSerializer(Double.serializer()),
          value
        )
      }!!
    )
  }?.associateBy { it.id } ?: error("embeddings not found")


// these are the inputs that we generated embeddings for
val inputs = mapOf(
  "input-1" to "banana muffin with chocolate chips",
  "input-2" to "apple crumble",
  "input-3" to "apple pie",
  "input-4" to "chocolate chip cookie",
  "input-5" to "the cookie monster",
  "input-6" to "pattiserie",
  "input-7" to "chicken teriyaki with rice",
  "input-8" to "tikka massala",
  "input-9" to "chicken",
)

// and we also generated embeddings for a few queries
val queries = mapOf(
  "q-1" to "rice",
  // pastry and pie, in Dutch
  "q-2" to "gebak en taart",
  "q-3" to "muppets",
  "q-4" to "artisanal baker",
  "q-5" to "indian curry",
  "q-6" to "japanese food",
  "q-7" to "baked goods",
)

// we'll use this simple data class as the model

@Serializable
data class KnnTestDoc(
  val id: String,
  val text: String,
  val vector: List<Double>)

val indexName = "knn-test"
client.createIndex(indexName) {
  mappings {
    keyword(KnnTestDoc::id)
    text(KnnTestDoc::text)
    // text-similarity-ada-001 has a dimension of 1024
    // which is also the maximum for dense vector
    denseVector(
      property = KnnTestDoc::vector,
      dimensions = 1024,
      index = true,
      similarity = KnnSimilarity.Cosine
    )
  }
}

client.bulk(target = indexName) {
  inputs.map { (id, text) ->
    val embedding =
      embeddings[id]?.embedding
        ?: error("no embedding")
    KnnTestDoc(id, text, embedding)
  }.forEach { doc ->
    create(doc)
  }
}

queries.forEach { (queryId, text) ->
  client.search(indexName) {
    knn = KnnQuery(
      field = KnnTestDoc::vector,
      queryVector = embeddings[queryId]!!.embedding,
      k = 3,
      numCandidates = 3
    )
  }.let { searchResponse ->
    println("query for vector of $text:")
    searchResponse.searchHits.forEach { hit ->
      println("${hit.id} - ${hit.score}: ${hit.parseHit<KnnTestDoc>().text}")
    }
    println("---")
  }
}
```

This prints:

```text
query for vector of rice:
699yWJABqQXJk1kWntpn - 0.93660545: chicken
6d9yWJABqQXJk1kWntpn - 0.9159527: chicken teriyaki with rice
5d9yWJABqQXJk1kWntpn - 0.91181946: apple pie
---
query for vector of gebak en taart:
5d9yWJABqQXJk1kWntpn - 0.8899498: apple pie
6t9yWJABqQXJk1kWntpn - 0.8890915: tikka massala
6N9yWJABqQXJk1kWntpn - 0.88838005: pattiserie
---
query for vector of muppets:
5d9yWJABqQXJk1kWntpn - 0.9113884: apple pie
699yWJABqQXJk1kWntpn - 0.90841866: chicken
599yWJABqQXJk1kWntpn - 0.90057564: the cookie monster
---
query for vector of artisanal baker:
5t9yWJABqQXJk1kWntpn - 0.9066143: chocolate chip cookie
599yWJABqQXJk1kWntpn - 0.89697075: the cookie monster
499yWJABqQXJk1kWntpn - 0.8922405: banana muffin with chocolate chips
---
query for vector of indian curry:
699yWJABqQXJk1kWntpn - 0.9322586: chicken
6d9yWJABqQXJk1kWntpn - 0.9312973: chicken teriyaki with rice
6t9yWJABqQXJk1kWntpn - 0.9182453: tikka massala
---
query for vector of japanese food:
6d9yWJABqQXJk1kWntpn - 0.92860985: chicken teriyaki with rice
699yWJABqQXJk1kWntpn - 0.9264393: chicken
5d9yWJABqQXJk1kWntpn - 0.9177284: apple pie
---
query for vector of baked goods:
5d9yWJABqQXJk1kWntpn - 0.9216957: apple pie
5t9yWJABqQXJk1kWntpn - 0.91723824: chocolate chip cookie
5N9yWJABqQXJk1kWntpn - 0.91052055: apple crumble
---
```

This shows both the power and weakness of knn search:

- the matches are vaguely semantic. However, the openai model doesn't perform very well.
- a few of the matches are clearly debatable or and the scoring seems a bit overly confident. For example, 
it doesn't seem to understand that the cookie monster is a muppet. 
- we are limited to a maximum dimension of 1024, this is why I selected text-similarity-ada-001 as the model.
better models are available.

So, use this at your own peril. Clearly, a lot depends on the AI model you use to calculate the embeddings.

If you wish to play with generating your own embeddings, I've published the source code for that 
[here](https://github.com/jillesvangurp/openai-embeddings-processor)



---

| [KT Search Manual](README.md) | Previous: [Creating Data Streams](DataStreams.md) | Next: [Extending the Json DSLs](ExtendingTheDSL.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |