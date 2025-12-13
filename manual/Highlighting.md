# Highlighting 

| [KT Search Manual](README.md) | Previous: [Join Queries](JoinQueries.md) | Next: [Reusing your Query logic](ReusableSearchQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |

---                

Highlighting allows you to show to your users why particular results are matching a query.

```kotlin
client.search(indexName) {
  query=match(TestDoc::name,"bananana") {
    fuzziness="AUTO"
  }
  // create a highlight on the name field with default settings
  highlight {
    add(TestDoc::name)
  }
}
```

{
    "took": 43,
    "_shards": {
        "total": 1,
        "successful": 1,
        "failed": 0,
        "skipped": 0
    },
    "timed_out": false,
    "hits": {
        "max_score": 0.7283795,
        "total": {
            "value": 1,
            "relation": "eq"
        },
        "hits": [
            {
                "_index": "docs-term-highlighting-demo",
                "_id": "2",
                "_score": 0.7283795,
                "_source": {
                    "id": "2",
                    "name": "Banana",
                    "tags": [
                        "fruit"
                    ],
                    "price": 0.8
                },
                "highlight": {
                    "name": [
                        "<em>Banana</em>"
                    ]
                }
            }
        ]
    }
}

Of course you can customize how highlighting works:

```kotlin
client.search(indexName) {
  query=match(TestDoc::name,"bananana") {
    fuzziness="AUTO"
  }
  // create a highlight on the name field with default settings
  highlight {
    // use some alternative tags instead of the defaults
    preTags="<pre>"
    postTags="</pre>"

    add(TestDoc::name) {
      // configure some per field settings
      type = Type.plain
      fragmenter=Fragmenter.span
    }
  }
}
```

{
    "took": 28,
    "_shards": {
        "total": 1,
        "successful": 1,
        "failed": 0,
        "skipped": 0
    },
    "timed_out": false,
    "hits": {
        "max_score": 0.7283795,
        "total": {
            "value": 1,
            "relation": "eq"
        },
        "hits": [
            {
                "_index": "docs-term-highlighting-demo",
                "_id": "2",
                "_score": 0.7283795,
                "_source": {
                    "id": "2",
                    "name": "Banana",
                    "tags": [
                        "fruit"
                    ],
                    "price": 0.8
                },
                "highlight": {
                    "name": [
                        "<pre>Banana</pre>"
                    ]
                }
            }
        ]
    }
}



---

| [KT Search Manual](README.md) | Previous: [Join Queries](JoinQueries.md) | Next: [Reusing your Query logic](ReusableSearchQueries.md) |
| [Github](https://github.com/jillesvangurp/kt-search) | &copy; Jilles van Gurp |  |