package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class IlmTest: SearchTestBase()  {
    @Test
    fun shouldSetUpIlmPolicy() = coRun {
        onlyOn("ilm only works on elasticsearch",
            SearchEngineVariant.ES7,
            SearchEngineVariant.ES8,
            SearchEngineVariant.ES9,
            ) {
            client.setIlmPolicy("my-ilm") {
                hot {
                    actions {
                        rollOver(2)
                    }
                }
                warm {
                    minAge(24.hours)
                    actions {
                        shrink(1)
                        forceMerge(1)
                    }
                }
            }
            println(client.getIlmPolicy("my-ilm"))
            client.deleteIlmPolicy("my-ilm")
            shouldThrow<RestException> {
                client.getIlmPolicy("my-ilm")
            }.status shouldBe 404
        }
    }
}