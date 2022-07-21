## Development status

KT Search is currently still under development. I'll release a 2.0 release (1.x being the legacy client) once I'm happy the functionality and documentation are complete enough. Part of this is internally upgrading the FORMATION backend to use my new client. I will likely discover some mistakes, bugs, and missing features as I do this.

However, the client is pretty feature complete already, and I have so far not found any show stopping issues. The 1.99.x series can be seen as a series of increasingly better release candidates.

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
