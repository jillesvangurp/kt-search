```kotlin
#!/usr/bin/env kotlin

@file:Repository("https://maven.tryformation.com/releases")
@file:Repository(" https://repo.maven.apache.org/maven2/")
@file:Repository("https://jitpack.io")
@file:DependsOn("com.github.jillesvangurp:kt-search-kts:1.0.7")

import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.clusterHealth
import com.jillesvangurp.ktsearch.kts.addClientParams
import com.jillesvangurp.ktsearch.kts.searchClient
import com.jillesvangurp.ktsearch.root
import kotlinx.cli.ArgParser
import kotlinx.coroutines.runBlocking

// ArgParser is included by kt-search-kts to allow you to configure the search endpoint
val parser = ArgParser("script")
// this adds the params for configuring search end point
val searchClientParams = parser.addClientParams()
parser.parse(args)

// extension function in kt-search-kts that uses the params
val client = searchClientParams.searchClient

// now use the client as normally in a runBlocking block (creates a co-routine)
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
```
