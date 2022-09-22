## Use cases

There are many places where you can use kt-search

- Add search functionality to your server applications. Kt-search works great with **Spring Boot**, Ktor, Quarkus, and other popular JVM based servers. Simply create your client as a singleton object and inject it wherever you need search.
- Use Kt-search in a **Kotlin-js** based web application to create **dashboards**, or web applications that don't need a separate server. See our [Full Stack at FORMATION](https://github.com/formation-res/kt-fullstack-demo) demo project for an example.
- Use **Kotlin Scripting** to operate and introspect your cluster. See the companion project [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts/) for more on this as well as the scripting section in the manual. The companion library combines `kt-search` with `kotlinx-cli` for command line argument parsing and provides some example scripts; all with the minimum of boiler plate.
- Use kt-search from a **Jupyter Notebook** with the Kotlin kernel. See the `jupyter-example` directory for an example and check the [README](jupyter-example/README.md) for instructions.

## Learn more

- [Release Notes](https://github.com/jillesvangurp/kt-search/releases)
- [Manual](https://jillesvangurp.github.io/kt-search/manual) - this is generated from the `docs` module. Just like this README.md file. The manual covers most of the extensive feature set of this library. Please provide feedback via the issue tracker if something is not clear to you.
- [API Documentation](https://jillesvangurp.github.io/kt-search/api/). Dokka documentation.
- You can also learn a lot by looking at the integration tests in the `search-client` module.
- The code sample below should help you figure out the basics.





