package com.jillesvangurp.eskotlinwrapper

import com.fasterxml.jackson.databind.ObjectMapper
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.create
import org.elasticsearch.client.indexRepository
import org.elasticsearch.xcontent.XContentType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.reflect.KClass

class TestIndex<T : Any>(
    val clazz: KClass<T>,
    val esClient: RestHighLevelClient,
    val repo: IndexRepository<T>,
    val asyncRepo: AsyncIndexRepository<T>
)

inline fun <reified T : Any, O> withTestIndex(
    index: String? = null,
    createIndex: Boolean = true,
    deleteIndexAfterTest: Boolean = createIndex,
    refreshAllowed: Boolean = true,
    mappingsAndSettingsResource: String = "/testmodel-settings.json",
    block: TestIndex<T>.() -> O
): O {
    val esClient = create(port = 9999)
    val repo = esClient.indexRepository(
        index = if(index != null) "$index-${UUID.randomUUID()}" else  "test-${UUID.randomUUID()}",
        refreshAllowed = refreshAllowed,
        modelReaderAndWriter = JacksonModelReaderAndWriter(
            T::class,
            ObjectMapper().findAndRegisterModules()
        )
    )
    val asyncRepo = esClient.asyncIndexRepository(
        index = index ?: "test-${UUID.randomUUID()}",
        refreshAllowed = refreshAllowed,
        modelReaderAndWriter = JacksonModelReaderAndWriter(
            T::class,
            ObjectMapper().findAndRegisterModules()
        )
    )
    if (createIndex) {
        val mappingsAndSettings = TestIndex::class.java.getResource(mappingsAndSettingsResource).readText()
        repo.createIndex {
            source(mappingsAndSettings, XContentType.JSON)
        }
    }
    val result = block.invoke(TestIndex(T::class, esClient, repo, asyncRepo))
    if (deleteIndexAfterTest) {
        repo.deleteIndex()
    }
    return result
}

open class AbstractElasticSearchTest(
    val indexPrefix: String = "test",
    val createIndex: Boolean = true,
    val deleteIndexAfterTest: Boolean = true
) {
    lateinit var repository: IndexRepository<TestModel>
    lateinit var asyncRepository: AsyncIndexRepository<TestModel>
    lateinit var esClient: RestHighLevelClient
    lateinit var indexName: String

    @BeforeEach
    fun before() {
        // sane defaults
        esClient = create(port = 9999)
        // each test gets a fresh index
        indexName = "$indexPrefix-" + randomId()

        repository = esClient.indexRepository(
            index = indexName,
            refreshAllowed = true,
            modelReaderAndWriter = JacksonModelReaderAndWriter(
                TestModel::class,
                ObjectMapper().findAndRegisterModules()
            )
        )
        asyncRepository = esClient.asyncIndexRepository(
            index = indexName,
            refreshAllowed = true,
            modelReaderAndWriter = JacksonModelReaderAndWriter(
                TestModel::class,
                ObjectMapper().findAndRegisterModules()
            )
        )

        if (createIndex) {
            val settings = this.javaClass.getResource("/testmodel-settings.json").readText()
            repository.createIndex {
                source(settings, XContentType.JSON)
            }
        }
    }

    @AfterEach
    fun after() {
        // delete the index after the test
        if (deleteIndexAfterTest) {
            repository.deleteIndex()
        }
    }
}
