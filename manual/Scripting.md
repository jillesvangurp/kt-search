# Scripting Search with KTS 

One interesting use of kt-search is to script common operations around Elasticsearch.

You may find a few example scripts in the `scripts` directory. To run these scripts,
you need to have kotlin installed on your system of course.

## Example script

```kotlin
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
```

This simple example script adds the maven dependency via `@file:` directives. 
You can then import the client as normally. Because the client uses suspending
 functions, you have to surround your code with a `runBlocking {...}`

## Some ideas for using kt-search on the cli

Some ideas for using `kts` scripting with Kt-Search:

- index creation and alias management
- bulk indexing content
- manage cluster settings
- orchestrate rolling restarts
- snapshot management

## Problems and solutions

Unfortunately, using kotlin script is a bit underdocumented by Jetbrains.

Some gotchas:

- your script name MUST end in `.main.kts`
- make sure to add the shebang to your script `#!/usr/bin/env kotlin` 
this will direct linux/mac to use kotlin to run the script
- intellij does not reliably reload the script context when you 
modify the dependencies: closing and re-opening the IDE seems to work.

