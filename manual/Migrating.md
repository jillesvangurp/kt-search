# Migrating from the old Es Kotlin Client 

| [KT Search Manual](README.md) | Previous: [Creating Data Streams](DataStreams.md) | Next: [Extending the Json DSLs](ExtendingTheDSL.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |
---                
Migrating from the old es-kotlin-client is quite easy but it is going to involve a bit of work:

- Packages have been renamed. This allows you to use the old client side by side with the new client.
- The search dsl has been extracted from the old client but is otherwise largely backwards compatible. So your queries should mostly work with the new client after fixing the package names.
- As the java `RestHighLevelClient` is no longer used, the kotlin APIs have changed. And that of course includes model classes for API responses that can no longer rely or depend on the old Java classes that came with the java client.
- Unlike the es-kotlin-client which generated kotlin `suspend` functions close to 100 api end points in the `RestHighLevelClient`, Kt Search only includes a handful of APIs for indexing, document crud, and of course searching. We have no intention to support the REST API in its entirety at this point. It's easy to support more of the REST API yourself using the `RestClient`.
- All APIs in `kt-search` are suspend only. Supporting blocking IO is not a priority and this gets rid of a lot of code duplication.



---
| [KT Search Manual](README.md) | Previous: [Creating Data Streams](DataStreams.md) | Next: [Extending the Json DSLs](ExtendingTheDSL.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |