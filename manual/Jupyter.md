# Jupyter Notebooks 

                | [KT Search Manual](README.md) | Previous: [Using Kotlin Scripting](Scripting.md) | - |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |
                ---                
                Using kt-search from jupyter and the kotlin kernel is easy! See the `jupyter-example` directory in the kt-search project.

## Install conda

On a mac, use home brew of course.

```bash
brew install miniconda
```

## Install jupyter with conda

Once you have conda installed, install jupyter and the kotlin kernel.

```bash
conda create -n kjupyter
conda activate kjupyter 
conda install jupyter
conda install -c jetbrains kotlin-jupyter-kernel
```

## Open the notebook

Now you are ready to open the notebook!

```bash
cd jupyter-example
jupyter notebook kt-search-example.ipynb
```

## Importing kt-search

Create a cell in your notebook with something like this:

```kotlin
@file:Repository("https://jitpack.io")
@file:DependsOn("com.github.jillesvangurp.kt-search:search-client:1.99.14")

import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.runBlocking

val client = SearchClient(
    KtorRestClient(
        host = "localhost",
        port = 9200
    )
)

runBlocking {
    val engineInfo = client.engineInfo()
    println(engineInfo.variantInfo.variant.name + ":" + engineInfo.version.number)
}
```

Note, you need to use `runBlocking` to use suspending calls on the client.

Otherwise, see the documentation for how to use the kotlin scripting support.


                ---
                | [KT Search Manual](README.md) | Previous: [Using Kotlin Scripting](Scripting.md) | - |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |