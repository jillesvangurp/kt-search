package com.jillesvangurp.searchdsls

enum class SearchEngineFamily {Elasticsearch, Opensearch}
enum class SearchEngineVariant(val family: SearchEngineFamily) {
    ES7(SearchEngineFamily.Elasticsearch),
    ES8(SearchEngineFamily.Elasticsearch),
    ES9(SearchEngineFamily.Elasticsearch),
    OS1(SearchEngineFamily.Opensearch),
    OS2(SearchEngineFamily.Opensearch),
    OS3(SearchEngineFamily.Opensearch)
}

annotation class VariantRestriction(vararg val variant: SearchEngineVariant)


