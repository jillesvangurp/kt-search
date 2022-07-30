## Development status

Currently, the client is feature complete, useful, and already better in many ways than the es-kotlin-client 1.x ever was. 

The 1.99.x series can be seen as a series of increasingly better release candidates/betas. If you do run into issues, please create an issue. Likewise, if there is important functionality that you need. I have so far not found any show stopping issues.

Before tagging a 2.0 release, I intend to port some of our internal code at FORMATION to use kt-search and dog food this extensively. I will likely discover new/missing features, add things I need, etc.

Until then, I reserve the right to refactor, rename, etc. things as needed. A 2.0 release will mark a commitment to API stability.

Non Goals:

- Full coverage of what the RestHighLevel client used to do. If you need it, just add that as a dependency or create your own extension function for `SearchClient`; it's not that hard. And of course consider creating a pull request if you do.
- Cover the entire search DSL. The provided Kotlin DSL is very easy to extend by design. If it doesn't directly support what you need, you can simply manipulate the underlying Map to add what you need. See the manual for instructions on how to do this. Alternatively, you can create a pull request to add proper type safe support for the feature that you want. Currently, most commonly used things in the DSL are already supported. We'll likely expand the features over time of course. Note, you can also create JsonDsls for other APIs. E.g. the snapshot API, the reindex API, etc. each have their own custom Json requests and responses. 

## Contributing

I'm tracking a few open issues and would appreciate any help of course. But until this stabilizes, reach out to me before doing a lot of work to create a pull request. Check [here](CONTRIBUTING.md) for mote details.

## Help, support, and consulting

Within reason, feel free to reach out via the issue tracker or other channels if you need help with this client. 

For bigger things, you may also want to consider engaging me as a consultant to help you resolve issues with your Elastic / Opensearch setup, optimizing your queries, etc. Please refer to my [website](https://www.jillesvangurp.com)

## About this README

It's generated using the same `kotlin4example` library that I also used for the old client documentation. It's great for including little code snippets that are actually executing as part of the tests and therefore know  correct. Also, I can refactor and have the documentation change as well without breaking.
