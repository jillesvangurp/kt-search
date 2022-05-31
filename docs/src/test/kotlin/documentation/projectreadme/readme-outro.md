## Development status

KT Search is currently still under development. So, expect refactoring, renames, package changes, etc. I'll release a 2.0 release (1.x being the legacy client). Until that happens, expect some refactoring. I plan to get to a 2.0 release quickly as I have a clear idea of what needs to be done and how based on my learnings from 1.0 (and the many Java clients I wrote for ES on various projects before that).

Currently there are no releases yet. I plan to address this soon with a release to (probably) maven central.

The search client module is the main module of this library. I extracted the json-dsl module and search-dsls module with the intention of eventually moving these to separate libraries. Json-dsl is useful for pretty much any kind of json dialect. And as my legacy-client module shows, the search-dsls can be used by other clients than the search-dsls module. In fact, I have a vague plan to start writing kotlin multi platform clients for other json APIs that are not related to search.

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. After that, you may use it as a migration path. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist for now. I am not planning to do any further maintenance on that, however. People are welcome to fork that project of course.

The future is going to be about using the pure kotlin multi-platform client. Of course, you may combine that with other clients; including the old RestHighLevel client or even the legacy client.

## Goals/todo's:

For more detail refer to the issue tracker. This is merely my high level plan.

- [x] Extract the kotlin DSLs to a multi platform module.
- [x] Rename the project to kt-search and give it its own repository
- [x] Implement a new client using ktor and kotlinx-serialization
- [ ] Port the IndexRepository to the new client
- [x] Port the bulk indexing functionality to the new client
- [ ] Update documentation for the new client
- [ ] Delete the legacy client module from this project once all functionality has been ported
- [ ] Extract an interface for the new client and provide alternate implementations. By extracting the dsl, I've created the possibility to use different JSON serialization and parsing strategies. So potentially we could support alternate http transport and parsing without too much trouble.
  - OkHttp / jackson?
  - Es rest client / jackson? - useful if you are interested in using this library and the Elastic client (either their new Java client or their deprecated RestHighLevelClient )
  - Os rest client / jackson? - useful if you are interested in using the Opensearch forks of the Elastic clients.

Once all these boxes are checked, I'm going to release a beta.

Non Goals:

- Full coverage of what the RestHighLevel client does. If you need it, just add that as a dependency or create your own extension function for `SearchClient`; it's not that hard.
- Cover the entire search DSL. The provided Kotlin DSL is easily extensible, so if it doesn't directly support what you need, you can simply manipulate the underlying Map to add what you need. Alternatively, you can create a pull request to add support for the feature that you want. Currently, most commonly used things in the DSL are already supported.

## Contributing

I'm tracking a few open issues and would appreciate any help of course. But until this stabilizes, reach out to me before doing a lot of work to create a pull request. Check the [here](CONTRIBUTING.md) for mote details.

## About this README

It's generated using the same `kotlin4example` library that I also used for the old client documentation. It's great for including little code snippets that are actually executing as part of the tests and therefore know  correct. Also, I can refactor and have the documentation change as well without breaking.
