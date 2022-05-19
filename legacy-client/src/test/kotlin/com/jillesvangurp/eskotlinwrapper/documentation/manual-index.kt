package com.jillesvangurp.eskotlinwrapper.documentation

val manualIndexMd by sourceGitRepository.md {
    +"""
                The [Elasticsearch Kotlin Client](https://github.com/jillesvangurp/es-kotlin-wrapper-client) is a client 
                library written in Kotlin that 
                adapts the [Highlevel Elasticsearch HTTP client for Java](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high.html) provided by Elasticsearch.
                
                ## Chapters
            """
    +manualPages.values.joinToString("\n") { "- ${com.jillesvangurp.kotlin4example.mdLink(it.title, it.fileName)}" }
    +"""
                
                ## Introduction
                
                The official Java client provides client functionality for essentially everything exposed by their REST
                API. The Elasticsearch Kotlin Client makes using this functionality more Kotlin friendly. 

                It does this
                through extension functions that add many useful features and shortcuts. It adds Kotlin DSLs for
                querying, defining mappings, and bulk indexing. To facilitate the most common use cases, this library
                also provides a Repository abstraction that enables the user to interact with an index in a way that
                is less boilerplate heavy.
                
                Additionally, it provides co-routine friendly versions of the asynchronous clients in the Java library.
                This enables the user to write fully reactive code in e.g. Ktor or Spring Boot. This makes this
                library the easiest way to do this currently.
            """
}