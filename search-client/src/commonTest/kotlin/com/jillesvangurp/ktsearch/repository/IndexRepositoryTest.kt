package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.*
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class IndexRepositoryTest : SearchTestBase() {

    @Test
    fun shouldDoCrudWithRepo() = coRun {
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
    fun shouldDoBulkWithRepo() = coRun{
        repo.createIndex {}
        repo.bulk {
            index(TestDocument("1").json())
            index(TestDocument("2").json())
            index(TestDocument("3").json())
        }
        val r = repo.search {  }
        r.total shouldBe 3
    }

    @Test
    fun shouldDoBulkUpdatesWithOptimisticLocking() = coRun{
        repo.createIndex {}

        repo.bulk {
            create(TestDocument("1").json(), id = "1")
            create(TestDocument("2").json(), id = "2")
        }

        repo.bulk(callBack = null) {
            update(repo.get("1").second) {
                it.copy(name="Changed 1")
            }
            // this one should trigger a retry
            update("2", TestDocument("2"),42,42) {
                it.copy(name = "Changed 2")
            }
        }

        repo.get("1").first.name shouldBe "Changed 1"
        repo.get("2").first.name shouldBe "Changed 2"
    }
}