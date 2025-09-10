package com.jillesvangurp.ktsearch

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AliasManagementTest: SearchTestBase() {

    @Test
    fun addRemoveAliases() = coRun{

        val name = randomIndexName()
        val index1 = "$name-1"
        val index2 = "$name-2"
        val aliasName = "alias-$name"
        client.createIndex(index1)
        client.createIndex(index2)

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

    @Test
    fun shouldUseUserFriendlyWayToManageAliases() = coRun {
        val name = randomIndexName()
        val index1 = "$name-1"
        val index2 = "$name-2"
        val alias1 = "alias-$name"
        val alias2 = "alias2-$name"

        client.createIndex(index1)
        client.createIndex(index2)

        client.getIndexesForAlias(alias1).shouldBeEmpty()
        client.getIndexesForAlias(alias2).shouldBeEmpty()
        // wrong request but an index is always aliased to itself
        client.getIndexesForAlias(index1) shouldContainExactlyInAnyOrder listOf(index1)
        client.getAliasNamesForIndex(index1).shouldBeEmpty()
        client.getAliasNamesForIndex(index2).shouldBeEmpty()

        client.updateAliases {
            addAliasForIndex(index1, alias1)
            addAliasForIndex(index2, alias1)
            addAliasForIndex(index2, alias2)
        }

        client.getIndexesForAlias(alias1) shouldContainExactlyInAnyOrder listOf(index1,index2)
        client.getIndexesForAlias(alias2) shouldContainExactlyInAnyOrder listOf(index2)
        client.getAliasNamesForIndex(index1) shouldContainExactlyInAnyOrder listOf(alias1)
        client.getAliasNamesForIndex(index2) shouldContainExactlyInAnyOrder listOf(alias1, alias2)

        client.updateAliases {
            removeAliasForIndex(index2,alias2)
            removeAliasForIndex(index1,alias1, true)
        }
        client.getIndexesForAlias(alias1) shouldContainExactlyInAnyOrder listOf(index2)
        client.getAliasNamesForIndex(index2) shouldContainExactlyInAnyOrder listOf(alias1)

        // index has been deleted
        shouldThrow<RestException> {
            client.getIndex(index1)
        }.status shouldBe 404
    }
}