# Module eskotlinwrapper

A kotlin client that adapts the Java HighLevel REST client from Elasticsearch. Note, much of this library consists of extension functions for classes in the `org.elasticsearch` hierarchy.

Typical usage would involve creating a client and a dao instance:

```
// a short cut for creating a client + rest client
// all of the parameters are optional if you want to run against localhost:9200
// you can also use the original RestHighLevelClient constructor from the java SDK
val esClient = RestHighLevelClient(
  host="domain.com",
  port=9999,
  https=true,
  user="thedude",
  password="lebowski")

// documents are backed by data classes   
data class TestModel(var message: String)

// the dao takes care of serialization/deserialization boilerplate, CRUD, and more
// you can of course use your own serialization framework of choice
val dao = esClient.crudDao<TestModel>("myindex", 
  refreshAllowed = true,
  modelReaderAndWriter = JacksonModelReaderAndWriter(
                TestModel::class,
                ObjectMapper().findAndRegisterModules()
  )
)
```

# Package com.jillesvangurp.eskotlinwrapper

Misc. classes that support the various extension functions on the client.

# Package org.elasticsearch.action.search

Extension functions to add a few alternate source methods on queries. E.g. you can use multiline Kotlin templated strings.
	
# Package org.elasticsearch.client	

Extension functions to add functions to the client. Like `RestHighLevelClient(...)` which is technically not a constructor ...

# Package org.elasticsearch.common.xcontent	

Extension functions to support strings more easily.

# Package org.elasticsearch.search

Extension functions to adapt search responses.
