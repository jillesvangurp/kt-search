package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MetricAggsTest {

    @Test
    fun `avg agg supports defaults and overrides`() {
        val defaultAgg = AvgAgg("duration")
        val defaultConfig = defaultAgg.config()

        defaultConfig["field"] shouldBe "duration"
        defaultConfig.containsKey("missing") shouldBe false
        defaultConfig.containsKey("script") shouldBe false

        val scriptedAgg = AvgAgg(
            field = null,
            missing = 0,
            script = Script.create { source = "doc['duration'].value" }
        )
        val scriptedConfig = scriptedAgg.config()

        scriptedConfig.containsKey("field") shouldBe false
        scriptedConfig["missing"] shouldBe 0
        scriptedConfig["script"] shouldBe scriptedAgg.script
    }

    @Test
    fun `value count agg supports defaults and overrides`() {
        val defaultAgg = ValueCountAgg("duration")
        val defaultConfig = defaultAgg.config()

        defaultConfig["field"] shouldBe "duration"
        defaultConfig.containsKey("missing") shouldBe false
        defaultConfig.containsKey("script") shouldBe false

        val scriptedAgg = ValueCountAgg(
            missing = 1,
            script = Script.create { source = "params.tagCount" }
        )
        val scriptedConfig = scriptedAgg.config()

        scriptedConfig.containsKey("field") shouldBe false
        scriptedConfig["missing"] shouldBe 1
        scriptedConfig["script"] shouldBe scriptedAgg.script
    }

    @Test
    fun `stats agg supports defaults and overrides`() {
        val defaultAgg = StatsAgg("duration")
        val defaultConfig = defaultAgg.config()

        defaultConfig["field"] shouldBe "duration"
        defaultConfig.containsKey("missing") shouldBe false
        defaultConfig.containsKey("script") shouldBe false

        val scriptedAgg = StatsAgg(
            missing = 0,
            script = Script.create { source = "params.total" }
        )
        val scriptedConfig = scriptedAgg.config()

        scriptedConfig.containsKey("field") shouldBe false
        scriptedConfig["missing"] shouldBe 0
        scriptedConfig["script"] shouldBe scriptedAgg.script
    }

    @Test
    fun `extended stats agg supports defaults and overrides`() {
        val defaultAgg = ExtendedStatsAgg("duration")
        val defaultConfig = defaultAgg.config()

        defaultConfig["field"] shouldBe "duration"
        defaultConfig.containsKey("missing") shouldBe false
        defaultConfig.containsKey("script") shouldBe false

        val scriptedAgg = ExtendedStatsAgg(
            missing = 0,
            script = Script.create { source = "params.total" }
        )
        val scriptedConfig = scriptedAgg.config()

        scriptedConfig.containsKey("field") shouldBe false
        scriptedConfig["missing"] shouldBe 0
        scriptedConfig["script"] shouldBe scriptedAgg.script
    }

    private fun AggQuery.config(): JsonDsl = this[this.name] as JsonDsl
}
