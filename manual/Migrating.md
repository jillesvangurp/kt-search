# Migrating from Es-Kotlin-Client 

Migrating from the old es-kotlin-client is quite easy but it is going to involve a bit of work:

- The search dsl has been extracted from the old client but is otherwise largely backwards compatible. So your queries should work with the new client.
- As the java `RestHighLevelClient` is no longer used, all of the kotlin APIs have changed. And that of course includes model classes for API responses.
- Unlike the es-kotlin-client which generated kotlin `suspend` functions close to 100 api end points in the `RestHighLevelClient`, Kt Search only includes a handful of APIs for indexing, document crud, and of course searching. We have no intention to support the REST API in its entirety at this point. It's easy to support more of the REST API yourself using the `RestClient`.

## Using the legacy client

To faciliate migrating to the new client, the old es-kotlin-client is preserved in the form of the legacy-client. It is largely the same code as the es-kotlin-client but with some changes to use the new search-dsl module.

You can use the legacy-client next to the kt-search client and gradually get rid of usages of the legacy-client. Once you are done, remove the dependency on the legacy-client

