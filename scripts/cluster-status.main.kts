#!/usr/bin/env kotlin

@file:Repository("https://maven.tryformation.com/releases")
@file:DependsOn("com.jillesvangurp:search-client-jvm:1.99.3")

import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.runBlocking

val client = SearchClient(
    KtorRestClient("localhost",9999)
)

runBlocking {
    val clusterStatus=client.clusterHealth()
    when(clusterStatus.status) {
        ClusterStatus.Green -> println("OK!")
        ClusterStatus.Yellow -> println("WARNING: cluster is yellow")
        ClusterStatus.Red -> println("ERROR: cluster is red!!!!!")
    }
}
