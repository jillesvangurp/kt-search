package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.SearchTestBase
import com.jillesvangurp.ktsearch.TestDocument
import com.jillesvangurp.ktsearch.coTest
import com.jillesvangurp.ktsearch.total
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IndexRepositoryTest : SearchTestBase() {

    @Test
    fun shouldDoCrudWithRepo() = coTest {
        val d = repo.index(TestDocument("1"))
        d.shards.successful shouldBeGreaterThan 0
        val (doc,_)= repo.get(d.id)
        doc.name shouldBe "1"

        val (doc2,_) =repo.update(d.id) {
            it.copy(name="2")
        }
        doc2.name shouldBe "2"
        val (doc3,_)= repo.get(d.id)
        doc3 shouldBe doc2
    }

    @Test
    fun shouldDoBulkWithRepo() = coTest{
        repo.createIndex {}
        repo.bulk {
            index(TestDocument("1").json())
            index(TestDocument("2").json())
            index(TestDocument("3").json())
        }
        val r = repo.search {  }
        r.total shouldBe 3
    }
}