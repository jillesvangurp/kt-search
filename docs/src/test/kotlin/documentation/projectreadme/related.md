## Related projects

There are several libraries that build on kt-search:

- [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts) - this library combines `kt-search` with `kotlinx-cli` to make scripting really easy. Combined with the out of the box support for managing snapshots, creating template mappings, bulk indexing, data-streams, etc. this is the perfect companion to script all your index operations. Additionally, it's a great tool to e.g. query your data, or build some health checks against your production indices.
- [kt-search-logback-appender](https://github.com/jillesvangurp/kt-search-logback-appender) - this is a logback appender that bulk indexes log events straight to elasticsearch. We use this at FORMATION.
- [full stack Kotlin demo project](https://github.com/formation-res/kt-fullstack-demo) A demo project that uses kt-search.
- [es-kotlin-client](https://github.com/jillesvangurp/es-kotlin-client) - version 1 of this client; now no longer maintained.

Additionally, I also maintain a few other search related projects that you might find interesting.

- [Rankquest Studio](https://rankquest.jillesvangurp.com) - A user friendly tool that requires no installation process that helps you build and run test cases to measure search relevance for your search products. Rankquest Studio of course uses kt-search but it is also able to talk directly to your search API and is designed to work with any kind of search api or product that is able to return lists of results.
- [querylight](https://github.com/jillesvangurp/querylight) - Sometimes Elasticsearch/Opensearch is just overkill. Query light is a tiny but capable in memory search engine that you can embed in your kotlin browser, server, or mobile applications. We use it at FORMATION to support e.g. in app icon search. Querylight comes with its own analyzers and query language. 