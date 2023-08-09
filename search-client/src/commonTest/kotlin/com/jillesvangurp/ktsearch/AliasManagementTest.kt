package com.jillesvangurp.ktsearch

import io.kotest.matchers.collections.shouldContain
import kotlin.test.Test

class AliasManagementTest: SearchTestBase() {

    @Test
    fun addRemoveAliases() = coRun{

        val name = randomIndexName()
        val index1 = "$name-1"
        val index2 = "$name-2"
        val aliasName = "alias-$name"
        client.createIndex(index1) {

        }
        client.createIndex(index2) {

        }

        client.updateAliases {
            add {
                alias= aliasName
                indices= listOf(index1, index2)
            }
        }

        client.getAliases(index1).let {
            it[index1]!!.aliases.keys shouldContain aliasName
        }
        client.getAliases().let {
            it[index2]!!.aliases.keys shouldContain aliasName
        }
        client.updateAliases {
            remove {
                alias= aliasName
                indices=listOf(index1)
            }
        }
        client.updateAliases {
            removeIndex {
                indices=listOf(index1, index2)
            }
        }
    }
}