package com.jillesvangurp.searchdsls.querydsl

interface QueryClauses

/**
 * Allows you to create a query without a search dsl context. Returns the [ESQuery] created by your [block].
 *
 * This makes it possible to create utility functions that aren't extension functions
 * on QueryClauses that construct queries. The core use case is reusable functionality for
 * building queries or parts of queries.
 *
 * It works by creating an anonymous object implementing QueryClauses and then passing that to [block].
 */
fun constructQueryClause(block: QueryClauses.() -> ESQuery): ESQuery {
    val obj = object : QueryClauses {}
    return block(obj)
}