## Why Kt-search?

If you develop software in Kotlin and would like to use Opensearch or Elasticsearch, you have a few choices to make. There are multiple clients to choose from and not all of them work for each version. And then there is Kotlin multi platform to consider as well. Maybe you are running spring boot on the jvm. Or maybe you are using ktor compiled to native or wasm and using that to run lambda functions. 

Kt-search has you covered for all of those. The official Elastic or Opensearch clients are Java clients. You can use them from Kotlin but only on the JVM. And they are not source compatible with each other. The Opensearch client is based on a fork of the old Java client which after the fork was deprecated. On top of that, it uses opensearch specific package names.

Kt-search solves a few important problems here:

- It's Kotlin! You don't have to deal with all the Java idiomatic stuff that comes with the three Java libraries. You can write pure Kotlin code, use co-routines, and use Kotlin DSLs for everything. Simpler code, easier to debug, etc.
- It's a multiplatform library. We use it on the jvm and in the browser (javascript). Wasm support is coming soon and there is also native and mobile support. So, your Kotlin code should be extremely portable. So, whether you are doing backend development, doing lambda functions, command line tools, mobile apps, or web apps, you can embed kt-search in each of those.
- It doesn't force you to choose between Elasticsearch or Opensearch. Some features are specific to those products and will only work for those platforms but most of the baseline functionality is exactly the same for both.
- It's future proof. Everything is extensible (DSLs) and modular. Even supporting custom plugins that add new features is pretty easy with the `json-dsl` library that is part of kt-search.

## License

This project is [licensed](LICENSE) under the MIT license.

## Learn more

- **[Manual](https://jillesvangurp.github.io/kt-search/manual)** - this is generated from the `docs` module. Just like this README.md file. The manual covers most of the extensive feature set of this library. Please provide feedback via the issue tracker if something is not clear to you. Or create a pull request to improve the manual.
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/). Dokka documentation. You can browse it, or access this in your IDE.
- [Release Notes](https://github.com/jillesvangurp/kt-search/releases).
- You can also learn a lot by looking at the integration tests in the `search-client` module.
- There's a [full stack Kotlin demo project](https://github.com/formation-res/kt-fullstack-demo) that we built to show off this library and a few other things.
- The code sample below should help you figure out the basics.

## Use cases

Integrate **advanced search** capabilities in your Kotlin applications. Whether you want to build a web based dashboard, an advanced ETL pipeline or simply expose a search endpoint as a microservice, this library has you covered. 

- Add search functionality to your server applications. Kt-search works great with **Spring Boot**, Ktor, Quarkus, and other popular JVM based servers. Simply create your client as a singleton object and inject it wherever you need search.
- Build complicated ETL functionality using the Bulk indexing DSL.
- Use Kt-search in a **Kotlin-js** based web application to create **dashboards**, or web applications that don't need a separate server. See our [Full Stack at FORMATION](https://github.com/formation-res/kt-fullstack-demo) demo project for an example.
- For dashboards and advanced querying, aggregation support is key and kt-search provides great support for that and makes it really easy to deal with complex nested aggregations.
- Use **Kotlin Scripting** to operate and introspect your cluster. See the companion project [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts/) for more on this as well as the scripting section in the [Manual](https://jillesvangurp.github.io/kt-search/manual/Scripting.html). The companion library combines `kt-search` with `kotlinx-cli` for command line argument parsing and provides some example scripts; all with the minimum of boiler plate.
- Use kt-search from a **Jupyter Notebook** with the Kotlin kernel. See the `jupyter-example` directory for an example and check the [Manual](https://jillesvangurp.github.io/kt-search/manual/Jupyter.html) for instructions.

The goal for kt-search is to be the **most convenient way to use opensearch and elasticsearch from Kotlin** on any platform where Kotlin is usable.

Kt-search is extensible and modular. You can easily add your own custom DSLs for e.g. things not covered by this library or any custom plugins you use. And while it is opinionated about using e.g. kotlinx.serialization, you can also choose to use alternative serialization frameworks, or even use your own http client and just use the search-dsl.