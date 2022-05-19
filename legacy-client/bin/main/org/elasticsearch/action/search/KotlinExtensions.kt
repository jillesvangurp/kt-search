package org.elasticsearch.action.search


import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import com.jillesvangurp.mapbacked.stringify
import mu.KLogger
import mu.KotlinLogging
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.plugins.SearchPlugin
import org.elasticsearch.search.SearchModule
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.xcontent.*
import java.io.InputStream
import java.io.Reader
import java.util.function.Supplier

private val logger: KLogger = KotlinLogging.logger { }

val LOGGING_DEPRECATION_HANDLER: DeprecationHandler = object : DeprecationHandler {
    override fun usedDeprecatedName(
        parserName: String?,
        location: Supplier<XContentLocation>?,
        usedName: String?,
        modernName: String?
    ) {
        logger.warn { "You are using a deprecated name $usedName. You should use $modernName at ${location?.get()?.javaClass?.name}:${location?.get()?.lineNumber}" }
    }

    override fun usedDeprecatedField(
        parserName: String?,
        location: Supplier<XContentLocation>?,
        usedName: String?,
        replacedWith: String?
    ) {
        logger.warn { "You are using a deprecated field $usedName. You should use $replacedWith at ${location?.get()?.javaClass?.name}:${location?.get()?.lineNumber}" }
    }

    override fun usedDeprecatedField(parserName: String?, location: Supplier<XContentLocation>?, usedName: String?) {
        logger.warn { "You are using a deprecated field $usedName at ${location?.get()?.javaClass?.name}:${location?.get()?.lineNumber}" }
    }
}

private fun searchModule(settings: Settings = Settings.EMPTY, plugins: List<SearchPlugin> = listOf()) =
    SearchModule(settings, false, plugins)

/**
 * Adds the missing piece to the SearchRequest API that allows you to paste raw json.
 * This makes sense in Kotlin because it has multiline strings and support for template variables.
 */
fun SearchRequest.source(
    json: String,
    deprecationHandler: DeprecationHandler = LOGGING_DEPRECATION_HANDLER,
    settings: Settings = Settings.EMPTY,
    plugins: List<SearchPlugin> = listOf()
) {
    XContentFactory.xContent(XContentType.JSON).createParser(
        NamedXContentRegistry(searchModule(settings, plugins).namedXContents),
        deprecationHandler,
        json
    ).use {
        source(SearchSourceBuilder.fromXContent(it))
    }
}

/**
 * Adds the missing piece to the CountRequest API that allows you to paste raw json.
 * This makes sense in Kotlin because it has multiline strings and support for template variables.
 */
fun CountRequest.source(
    json: String,
    deprecationHandler: DeprecationHandler = LOGGING_DEPRECATION_HANDLER,
    settings: Settings = Settings.EMPTY,
    plugins: List<SearchPlugin> = listOf()
) {
    XContentFactory.xContent(XContentType.JSON).createParser(
        NamedXContentRegistry(searchModule(settings, plugins).namedXContents),
        deprecationHandler,
        json
    ).use {
        query(SearchSourceBuilder.fromXContent(it).query())
    }
}

/**
 * Adds the missing piece to the SearchRequest API that allows you to paste raw using a Reader. Useful if you store your queries in files.
 */
fun SearchRequest.source(
    reader: Reader,
    deprecationHandler: DeprecationHandler = LOGGING_DEPRECATION_HANDLER,
    settings: Settings = Settings.EMPTY,
    plugins: List<SearchPlugin> = listOf()
) {

    XContentFactory.xContent(XContentType.JSON).createParser(
        NamedXContentRegistry(searchModule(settings, plugins).namedXContents),
        deprecationHandler,
        reader
    ).use {
        source(SearchSourceBuilder.fromXContent(it))
    }
}

fun SearchRequest.configure(
    pretty: Boolean = false,
    debug: Boolean = false,
    settings: Settings = Settings.EMPTY,
    plugins: List<SearchPlugin> = listOf(),
    block: SearchDSL.() -> Unit
): SearchDSL {
    val searchDSL = SearchDSL()
    block.invoke(searchDSL)
    searchDSL.toString()
    val query = searchDSL.stringify(pretty)
    source(query, settings = settings, plugins = plugins)
    if(debug) {
        logger.info { searchDSL.stringify(pretty = true) }
    }
    return searchDSL
}

@Deprecated("Use SearchRequest.configure")
fun SearchRequest.dsl(pretty: Boolean = false, block: SearchDSL.() -> Unit): SearchDSL {
    val searchDSL = SearchDSL()
    block.invoke(searchDSL)
    val query = searchDSL.stringify(pretty)
    source(query)
    return searchDSL
}

/**
 * Adds the missing piece to the CountRequest API that allows you to paste raw using a Reader. Useful if you store your queries in files.
 */
fun CountRequest.source(
    reader: Reader,
    deprecationHandler: DeprecationHandler = LOGGING_DEPRECATION_HANDLER,
    settings: Settings = Settings.EMPTY,
    plugins: List<SearchPlugin> = listOf()
) {

    XContentFactory.xContent(XContentType.JSON).createParser(
        NamedXContentRegistry(searchModule(settings, plugins).namedXContents),
        deprecationHandler,
        reader
    ).use {
        query(SearchSourceBuilder.fromXContent(it).query())
    }
}

fun CountRequest.configure(pretty: Boolean = false, block: SearchDSL.() -> Unit): SearchDSL {
    val searchDSL = SearchDSL()
    block.invoke(searchDSL)
    val query = searchDSL.stringify(pretty)
    source(query)
    return searchDSL
}

/**
 * Supports taking the query straight from an InputStream. You probably should use the reader version.
 */
fun SearchRequest.source(
    inputStream: InputStream,
    deprecationHandler: DeprecationHandler = LOGGING_DEPRECATION_HANDLER,
    settings: Settings = Settings.EMPTY, plugins: List<SearchPlugin> = listOf()
) {
    XContentFactory.xContent(XContentType.JSON).createParser(
        NamedXContentRegistry(searchModule(settings, plugins).namedXContents),
        deprecationHandler,
        inputStream
    ).use {
        source(SearchSourceBuilder.fromXContent(it))
    }
}

fun SearchRequest.source(block: SearchSourceBuilder.() -> Unit) {
    val builder = SearchSourceBuilder()
    block.invoke(builder)
    source(builder)
}

/**
 * Supports taking the query straight from an InputStream. You probably should use the reader version.
 */
fun CountRequest.source(
    inputStream: InputStream,
    deprecationHandler: DeprecationHandler = LOGGING_DEPRECATION_HANDLER,
    settings: Settings = Settings.EMPTY,
    plugins: List<SearchPlugin> = listOf()
) {
    XContentFactory.xContent(XContentType.JSON).createParser(
        NamedXContentRegistry(searchModule(settings, plugins).namedXContents),
        deprecationHandler,
        inputStream
    ).use {
        query(SearchSourceBuilder.fromXContent(it).query())
    }
}

fun CountRequest.source(block: SearchSourceBuilder.() -> Unit) {
    val builder = SearchSourceBuilder()
    block.invoke(builder)
    query(builder.query())
}
