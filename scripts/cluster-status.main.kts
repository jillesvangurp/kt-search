#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.5")
@file:Repository("https://maven.tryformation.com/releases")
@file:DependsOn("com.jillesvangurp:search-client-jvm:1.99.3")

import com.jillesvangurp.ktsearch.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking

val parser = ArgParser("script")

val host by parser.option(ArgType.String, shortName = "a", fullName = "host", description = "Host").default("localhost")
val port by parser.option(ArgType.Int, shortName = "p", fullName = "port", description = "Port").default(9200)
val user by parser.option(ArgType.String, fullName = "user", description = "Basic authentication user name if using with cloud hosting")
val password by parser.option(ArgType.String, fullName = "password", description = "Basic authentication user name if using with cloud hosting")
val ssl by parser.option(ArgType.Boolean, fullName = "protocol", description = "Use https if true").default(false)
parser.parse(args)

val client = SearchClient(
    // our docker test cluster runs on port 9999
    // you may want to use 9200 with your own cluster
    KtorRestClient(
        host = host,
        port = port,
        user = user,
        password = password,
        https = ssl
    )
)

runBlocking {
    val clusterStatus=client.clusterHealth()
    client.root().let {
        println(
            """
                Cluster name: ${it.clusterName}
                Search Engine distribution: ${it.version.distribution}
                Version: ${it.version.number}              
            """.trimIndent()
        )
    }

    when(clusterStatus.status) {
        ClusterStatus.Green -> println("Relax, your cluster is green!")
        ClusterStatus.Yellow -> println("WARNING: cluster is yellow")
        ClusterStatus.Red -> error("OMG: cluster is red!!!!!")
    }
}
