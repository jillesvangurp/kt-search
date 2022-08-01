```kotlin
#!/usr/bin/env kotlin

@file:Repository("https://jitpack.io")
@file:Repository("https://maven.tryformation.com/releases")
@file:DependsOn("com.github.jillesvangurp:kt-search-kts:0.1.3")

import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.clusterHealth
import com.jillesvangurp.ktsearch.kts.addClientParams
import com.jillesvangurp.ktsearch.kts.searchClient
import com.jillesvangurp.ktsearch.root
import kotlinx.cli.ArgParser
import kotlinx.coroutines.runBlocking

val parser = ArgParser("script")
val searchClientParams = parser.addClientParams()
parser.parse(args)

val client = searchClientParams.searchClient

// now use the client as normally in a runBlocking block
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
