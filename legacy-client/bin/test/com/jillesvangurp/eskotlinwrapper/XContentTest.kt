package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.elasticsearch.xcontent.stringify
import org.elasticsearch.xcontent.xContentBuilder
import org.junit.jupiter.api.Test

class XContentTest {
    @Test
    fun `it should combine XContentBuilders`() {
        val stringified = xContentBuilder(
            mapOf(
                "mappings" to mapOf(
                    "properties" to mapOf(
                        "title" to xContentBuilder(
                            mapOf(
                                "type" to "long"
                            )
                        )
                    )
                )
            )
        ).stringify()
        assertThat(stringified).isEqualTo("""{"mappings":{"properties":{"title":{"type":"long"}}}}""")
    }
}
