package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.IndexRepository
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlin.test.Test

class ParentChildQueryTest: SearchTestBase() {

    @Serializable
    data class JoinField(val name: String, val parent: String? = null)

    @Serializable
    data class TestDoc(val name: String, val joinField: JoinField)

    private val parent1 = "parent-1"
    private val parent2 = "parent-2"
    private val child1 = "child-1"

    @Test
    fun shouldCreateJoinMappingAndQueryForDocumentsWithChildren() = coRun {
        val repo = setupIndex()

        val emptySearch = repo.search { }
        emptySearch.total shouldBe 3

        val childrenSearch = repo.search {
            query = hasChild("child") {
                query = matchAll()
            }
        }
        childrenSearch.total shouldBe 1
        childrenSearch.hits?.hits?.first()?.id shouldBe parent1
    }

    @Test
    fun shouldCreateJoinMappingAndQueryForDocumentsWithParents() = coRun {
        val repo = setupIndex()

        val emptySearch = repo.search {  }
        emptySearch.total shouldBe 3

        val parentSearch = repo.search {
            query = hasParent("parent") {
                query = matchAll()
            }
        }
        parentSearch.total shouldBe 1
        parentSearch.hits?.hits?.first()?.id shouldBe child1
    }

    @Test
    fun shouldQueryForAndReturnChildrenInInnerHits() = coRun {
        val repo = setupIndex()

        val emptySearch = repo.search {  }
        emptySearch.total shouldBe 3

        val childrenSearch = repo.search {
            query = hasChild("child") {
                query = matchAll()
                innerHits()
            }
        }
        val firstHit = childrenSearch.hits?.hits?.first()
        val innerHits = firstHit?.innerHits?.get("child")?.hits

        childrenSearch.total shouldBe 1
        firstHit?.id shouldBe parent1
        innerHits?.total?.value shouldBe 1
        innerHits?.hits?.first()?.id shouldBe child1
    }

    @Test
    fun shouldReturnChildrenWithParentId() = coRun {
        val repo = setupIndex()

        val emptySearch = repo.search {  }
        emptySearch.total shouldBe 3

        val childrenSearch = repo.search {
            query = parentId("child", parent1)
        }

        childrenSearch.total shouldBe 1
        childrenSearch.hits?.hits?.first()?.id shouldBe child1
    }

    private suspend fun setupIndex(): IndexRepository<TestDoc> {
        val index = randomIndexName()
        val mapping = IndexSettingsAndMappingsDSL().apply {
            mappings(dynamicEnabled = false) {
                text("name")
                join("joinField") {
                    relations("parent" to listOf("child"))
                }
            }
        }
        client.createIndex(index, mapping)

        val repo = client.repository(index, TestDoc.serializer())

        repo.index(TestDoc(name = parent1, joinField = JoinField(name = "parent")), id = parent1)
        repo.index(TestDoc(name = parent2, joinField = JoinField(name = "parent")), id = parent2)
        repo.index(TestDoc(name = child1, joinField = JoinField(name = "child", parent = parent1)), id = child1, routing = parent1)

        return repo
    }
}