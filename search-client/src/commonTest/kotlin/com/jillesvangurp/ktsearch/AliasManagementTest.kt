package com.jillesvangurp.ktsearch

import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test

class AliasManagementTest: SearchTestBase() {

    @Test
    fun addRemoveAliases() = coRun{
        client.createIndex("foo-1") {

        }
        client.createIndex("foo-2") {

        }

        client.updateAliases {
            add {
                alias="foo"
                indices= listOf("foo-1", "foo-2")
            }
        }

        client.getAliases("foo-1").let {
            it["foo-1"]!!.aliases.keys shouldContain "foo"
        }
        client.getAliases().let {
            it["foo-2"]!!.aliases.keys shouldContain "foo"
        }
        client.updateAliases {
            remove {
                alias="foo"
                indices=listOf("foo-1")
            }
        }
        client.updateAliases {
            removeIndex {
                indices=listOf("foo-1", "foo-2")
            }
        }
    }
}