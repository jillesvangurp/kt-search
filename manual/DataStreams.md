# Creating Data Streams 

                | [KT Search Manual](README.md) | Previous: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) | Next: [Migrating from the old Es Kotlin Client](Migrating.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |
                ---                
                Particularly for large volume time series data, you can use data streams to make the management of 
indices a bit easier. Data streams allow you to automate a lot of the things you would otherwise do manually 
with manually [Indices, Settings, Mappings, and Aliases](IndexManagement.md).          

## Index Templates

```kotlin

client.setIlmPolicy("my-ilm") {
  hot {
    actions {
      rollOver(2)
    }
  }
  warm {
    minAge(24.hours)
    actions {
      shrink(1)
      forceMerge(1)
    }
  }
}
// using component templates is a good idea
client.updateComponentTemplate("my-logs-settings") {
  settings {
    replicas = 4
    this["index.lifecycle.name"] = "my-ilm"
    put("index.lifecycle.name", "my-lifecycle-policy")
  }
}
client.updateComponentTemplate("my-logs-mappings") {
  mappings {
    text("name")
    keyword("category")
    // note data streams require @timestamp
    date("@timestamp")
  }
}
// now create the template
client.createIndexTemplate("my-logs-template") {
  indexPatterns = listOf("logs*")
  // make sure to specify an empty object for data_stream
  dataStream = withJsonDsl {
    // the elastic docs are a bit vague on what goes here
  }
  composedOf = listOf("logs-settings", "logs-mappings")
}

client.createDataStream("logs")
```

->

```
{"acknowledged":true}
```

## Index Life Cycle Management

It is advisable to set up index life cycle management (ILM) with data streams. Using ILM, you can automatically roll over indices, shrink them and delete them.

Index life cycle management is an Elastic only feature. However, Opensearch has a similar feature, called Index State Management. Which at this point we do not support. Pull requests are welcome for this.

```kotlin
client.setIlmPolicy("my-ilm") {
  hot {
    actions {
      rollOver(2)
    }
  }
  warm {
    minAge(24.hours)
    actions {
      shrink(1)
      forceMerge(1)
    }
  }
}
```


                ---
                | [KT Search Manual](README.md) | Previous: [Efficiently Ingest Content Using Bulk Indexing](BulkIndexing.md) | Next: [Migrating from the old Es Kotlin Client](Migrating.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp | [![](https://jitpack.io/v/jillesvangurp/kt-search.svg)](https://jitpack.io/#jillesvangurp/kt-search) |