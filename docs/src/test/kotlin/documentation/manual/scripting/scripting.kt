package documentation.manual.scripting

import documentation.sourceGitRepository

val scriptingMd = sourceGitRepository.md {
    +"""
        One interesting use of kt-search is to script common operations around Elasticsearch.
        
        You may find a few example scripts in the `scripts` directory. To run these scripts,
        you need to have kotlin installed on your system of course.
    """.trimIndent()

    section("Example script") {
        mdCodeBlock("""
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
        """.trimIndent())
    }

    +"""
        This simple example script adds the maven dependency via `@file:` directives. 
        You can then import the client as normally. Because the client uses suspending
         functions, you have to surround your code with a `runBlocking {...}`
     """
    section("Some ideas for using kt-search on the cli") {
        +"""         
             Some ideas for using `kts` scripting with Kt-Search:
             
             - index creation and alias management
             - bulk indexing content
             - manage cluster settings
             - orchestrate rolling restarts
             - snapshot management
        """.trimIndent()
    }

    section("How to run `.main.kts` scripts") {
        +"""
            To be able to run the scripts, install kotlin 1.7 via your linux package manager, 
            home-brew, sdkman, snap, etc. There are many ways to do this.
                        
            Unfortunately, using kotlin script is a bit under-documented by Jetbrains and still has some issues.
            
            Some gotchas:
            
            - your script name **MUST** end in `.main.kts`
            - kotlin scripting does not understand multi-platform, add `-jvm` suffix for the `kt-client` dependency
            - if you add a custom repository, you also have to specify maven central as a repository explicitly if you need more dependencies
```kotlin
@file:Repository("https://repo1.maven.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.5")
@file:Repository("https://maven.tryformation.com/releases")
@file:DependsOn("com.jillesvangurp:search-client-jvm:1.99.3")
```            
            - make sure to add the shebang to your script `#!/usr/bin/env kotlin` and of 
            course make it executable `chmod 755 myscript.main.kts`
            this will direct linux/mac to use kotlin to run the script with kotlin
            - intellij does not reliably reload the script context when you 
            modify the dependencies: closing and re-opening the IDE seems to work.
        """.trimIndent()

    }
}