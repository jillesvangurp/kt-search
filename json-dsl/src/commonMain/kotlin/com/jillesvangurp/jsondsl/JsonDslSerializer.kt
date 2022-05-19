package com.jillesvangurp.jsondsl

interface JsonDslSerializer {
    fun serialize(properties: JsonDsl, pretty: Boolean=false): String
}


