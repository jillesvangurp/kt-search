package com.jillesvangurp.ktsearch

class AuthenticationTest {
//    @Test run manually with correct credentials to test if auth is working FIXME better auth test than this but seems to work now
    fun shouldAddBasicAuth() = coRun {

        val client = SearchClient(
            KtorRestClient(
                https = true,
                host = "xxxxxx.europe-west3.gcp.cloud.es.io",
                port = 9243,
                user = "elastic",
                password = "xxxxx",
                logging = true
            )
        )
        println(client.clusterHealth())

    }
}