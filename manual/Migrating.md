# Migrating from the old Es Kotlin Client 

| [KT Search Manual](README.md) | Previous: [Jupyter Notebooks](Jupyter.md) | - |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Migrating from the old es-kotlin-client is quite easy but it is going to involve a bit of work:

- Packages have been renamed. This allows you to use the old client side by side with the new client.
- The search dsl has been extracted from the old client but is otherwise largely backwards compatible. So, your queries should mostly work with the new client after fixing the package names.
- As the java `RestHighLevelClient` is no longer used, the kotlin APIs have changed. And that of course includes model classes for API responses that can no longer rely or depend on the old Java classes that came with the java client.
- Unlike the es-kotlin-client which generated kotlin `suspend` functions close to 100 api end points in the `RestHighLevelClient`, Kt Search only includes a handful of APIs for indexing, document crud, and of course searching. We have no intention to support the REST API in its entirety at this point. However, it's really easy to support more of the REST API yourself using the `RestClient` and to define `JsonDsl` based model classes for their request payloads.
- All APIs in `kt-search` are suspend only. Supporting blocking IO is not a priority and this gets rid of a lot of code duplication.

For the migration of the FORMATION code base (this is the company that I am the CTO of), we managed to convert most 
of the code without too much problems. We ran with both the old and the new client for several months before getting rid
of the old client completely. Most of our queries did not need changes. Though of course, we were able to make 
them a bit nicer using several of the new features.




---

| [KT Search Manual](README.md) | Previous: [Jupyter Notebooks](Jupyter.md) | - |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |