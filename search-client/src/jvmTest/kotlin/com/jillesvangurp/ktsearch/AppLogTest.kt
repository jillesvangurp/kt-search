package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.util.*

class AppLogTest {

    // don't talk to es cloud in default test runs
//    @Test
    fun shouldConnectToEsAppLoggCluster() {
        // use this to test connectivity with elastic cloud
        // we've had this break a few times after changes in ktor client
        // put your properties in local.properties, this file is git ignored and
        // should not be committed
        val properties = Properties()
        properties.load(FileInputStream("../local.properties"))

        val host=properties.getProperty("esHost")
        val port=properties.getProperty("esPort").toInt()
        val https=properties.getProperty("esHttps").toBoolean()
        val user=properties.getProperty("esUser")
        val password = properties.getProperty("esPassword")
        password shouldNotBe null
        val searchClient = SearchClient(KtorRestClient(
            host = host,
            port = port,
            user = user,
            password = password,
            https = https,
            logging = true
        ))
        runBlocking {
            // should not throw exceptions because of authorization
            println(searchClient.engineInfo().version)
        }
    }
}