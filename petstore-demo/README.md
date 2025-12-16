# KT Search Pet Store Demo

This module is a small Spring Boot app that demonstrates
how to combine the kt-search DSLs with a search-friendly
read model. 

It has a few features to make it a bit more interesting than
your average hello world application:

- Rich search across several fields and search as you type.
- Uses aggregations by price, tag, etc. And of course you can **filter** on these.
- Dashboard with **apache echarts widgets powered* by aggregations as well.
- Use kt-search to define mappings
- Organize your indices with read and write aliases
- You don't index what you store but should use an **ETL pipeline**. This demo has one that **enriches 
the document with images and wikipedia articles**.
- There are separate indices for searching and storing. Normally you would use a database perhaps. But whenever 
something changes it needs to be also added to the search index.
Additionally, your code might require you to reindex, so that 
is built into this demo. 

![petstore demo](petstore-demo.webp)

## Run it locally

The compose file in this module mirrors the root
docker-compose setup and starts an Elasticsearch 9.x node
on port 9200.

```bash
cd petstore-demo
docker compose up elasticsearch -d
./gradlew :petstore-demo:bootRun
```

The SPA lives at http://localhost:8080 and talks to the API
on the same host.

## Wiring the search client

The Spring config uses the Ktor-based REST client that ships
with kt-search. Swap the hostname or credentials in
`application.yml` if you want to point the demo at a
different cluster.

```kotlin
// The Ktor-based REST client powers both the DSL-based repository
// helpers and the lower-level raw requests we occasionally need.
val elastic = properties.elastic
return SearchClient(
  KtorRestClient(
    host = elastic.host,
    port = elastic.port,
    https = elastic.https,
    user = elastic.username,
    password = elastic.password,
    // Very verbose but allows you to
    // see all the interactions with Elasticsearch
    logging = true
  ),
)
```

## Indices, aliases, and mappings

On startup we create versioned indices plus read/write
aliases so future rollovers become a metadata-only change.

```kotlin
// We maintain separate storage and search projections. Each gets its
// own read/write aliases so rolling over to a new version becomes a
// metadata-only operation instead of a reindex.
ensureIndexExists(
  indexName = properties.indices.petsIndex,
  readAlias = properties.indices.petsRead,
  writeAlias = properties.indices.petsWrite,
  repository = petsRepository,
  mappingBuilder = this::petsMapping
)

ensureIndexExists(
  indexName = properties.indices.petSearchIndex,
  readAlias = properties.indices.petSearchRead,
  writeAlias = properties.indices.petSearchWrite,
  repository = petSearchRepository,
  mappingBuilder = this::petSearchMapping
)
```

Raw documents live in `pets` with a strict mapping; the
search projection in `pet-search` uses a more flexible,
text-friendly mapping.

```kotlin
settings {
  shards = 1
  replicas = 0
}
mappings(dynamicEnabled = false) {
  keyword(Pet::id)
  keyword(Pet::animal) { index = false; store = true }
  keyword(Pet::breed) { index = false; store = true }
  keyword(Pet::sex) { index = false; store = true }
  field("age", "integer") { index = false; store = true }
  field("price", "double") { index = false; store = true }
  text(Pet::name) { index = false; store = true }
  text(Pet::description) { index = false; store = true }
  keyword(Pet::traits) { index = false; store = true }
  keyword("imageUrl") { index = false; store = true }
}
```

```kotlin
settings {
  shards = 1
  replicas = 0
}
mappings(dynamicEnabled = true) {
  keyword(PetSearchDocument::id)
  keyword(PetSearchDocument::animal)
  keyword(PetSearchDocument::breed)
  keyword(PetSearchDocument::sex)
  field("age", "integer")
  field("price", "double")
  keyword(PetSearchDocument::priceBucket)
  // Store traits as text for matching and keep a keyword subfield for
  // aggregations or exact matching.
  text(PetSearchDocument::traits) {
    fields { keyword("keyword") }
  }
  text(PetSearchDocument::name)
  text(PetSearchDocument::description)
  keyword("wikipediaUrl")
  keyword("image_url")
}
```

## Enriching pets before search

Every pet is augmented with helper data (hero image,
Wikipedia link, price bucket) before landing in the search
index. That keeps the UI payload compact and search results
consistent.

```kotlin
private fun Pet.toSearchDocument(): PetSearchDocument {
  val normalizedAnimal = animal.lowercase()
  val normalizedBreed = breed.lowercase()
  val animalBreedKey = "$normalizedAnimal|$normalizedBreed"
  val wiki = wikiLookup[animalBreedKey]
    ?: wikiLookup["$normalizedAnimal|$normalizedAnimal"]
  val image = imageUrl
    ?: stockImages[animalBreedKey]
    ?: stockImages["$normalizedAnimal|$normalizedAnimal"]
  // Use a coarse price bucket that lines up with the UI facets and the
  // aggregation configuration.
  val priceBucket = when {
    price < 500 -> "budget"
    price < 1500 -> "mid"
    else -> "premium"
  }
  return PetSearchDocument(
    id = id,
    name = name,
    animal = animal,
    breed = breed,
    sex = sex,
    age = age,
    price = price,
    priceBucket = priceBucket,
    description = description,
    traits = traits,
    wikipediaUrl = wiki,
    imageUrl = image
  )
}
```

## Search flow

The API builds a bool query with optional filters and a
dis_max clause to blend typo-friendly prefix matching with
best_fields relevance. Aggregations back the UI facets.

```kotlin
val filters = collectFilters(animal, breed, sex, ageRange, priceRange)
// The UI sends optional filters plus a free-form search string. We
// translate that into a bool query with filters and a dis_max clause
// that mixes best_fields and phrase_prefix matches to keep results
// relevant even with typos.
val response = rawSearch<PetSearchDocument>(
  target = properties.indices.petSearchRead
) {
  from = 0
  resultSize = 100
  applySearchQuery(searchText, filters)
  highlight {
    preTags = "<mark>"
    postTags = "</mark>"
    requireFieldMatch = false
    order = Order.score
    add(PetSearchDocument::name) {
      numberOfFragments = 0
    }
    add(PetSearchDocument::breed) {
      numberOfFragments = 0
    }
    add(PetSearchDocument::animal) {
      numberOfFragments = 0
    }
    add(PetSearchDocument::description) {
      fragmentSize = 120
      numberOfFragments = 2
    }
    add("traits") {
      numberOfFragments = 0
    }
  }
  agg("animals", TermsAgg("animal") { aggSize = 10 })
  agg("breeds", TermsAgg("breed") { aggSize = 20 })
  agg("sexes", TermsAgg("sex") { aggSize = 5 })
  agg(
    "ages",
    RangesAgg("age") {
      ranges = listOf(
        AggRange.create { to = 2.0; key = "0-2" },
        AggRange.create { from = 2.0; to = 8.0; key = "2-8" },
        AggRange.create { from = 8.0; key = "8+" }
      )
    }
  )
  agg(
    "prices",
    RangesAgg("price") {
      ranges = listOf(
        AggRange.create { to = 500.0; key = "budget" },
        AggRange.create { from = 500.0; to = 1500.0; key = "mid" },
        AggRange.create { from = 1500.0; key = "premium" }
      )
    }
  )
}
```

## Seeding demo data

On startup we lazily create the indices and load the bundled
JSON dataset so the UI always has something to show.

```kotlin
// Run the startup tasks on a background coroutine so Spring can finish
// its lifecycle hooks without blocking the main thread.
scope.launch {
  // Create indices if needed and then seed the demo dataset exactly
  // once (unless the user explicitly resets).
  petStoreService.ensureIndices()
  val resource = resourceLoader.getResource(properties.sampleData)
  resource.inputStream.use { stream ->
    petStoreService.loadSamplePetsIfEmpty(stream)
  }
}
```

