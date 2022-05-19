package com.jillesvangurp.eskotlinwrapper

import java.util.UUID

// use random ids so we don't have conflicting tests
internal fun randomId() = UUID.randomUUID().toString()
