package com.jillesvangurp.ktsearch

import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

class IlmTest: SearchTestBase()  {
    @Test
    fun shouldSetUpIlmPolicy() = coTest {
        onlyOn("ilm only works on elasticsearch",
            SearchEngineVariant.ES7,
            SearchEngineVariant.ES8) {
            client.ilm("my-ilm") {
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
        }
    }
}