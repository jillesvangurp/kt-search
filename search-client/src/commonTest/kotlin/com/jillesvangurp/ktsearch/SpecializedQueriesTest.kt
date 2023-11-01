package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.distanceFeature
import com.jillesvangurp.searchdsls.querydsl.rankFeature
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class SpecializedQueriesTest : SearchTestBase() {

    @Test
    fun shouldRankOnFeature() = coRun {
        val index = testDocumentIndex()
        client.bulk(target = index, refresh = Refresh.WaitFor) {
            create(
                TestDocument(
                    name = "p1",
                    point = listOf(12.0, 50.0),
                    timestamp = Clock.System.now().minus(10.days)
                )
            )
            create(
                TestDocument(
                    name = "p2",
                    point = listOf(13.0, 52.0),
                    timestamp = Clock.System.now().minus(5.days)
                )
            )
        }

        client.search(target = index) {
            query = distanceFeature(TestDocument::timestamp, "30d", "now-40d")
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p1"
        client.search(target = index) {
            query = distanceFeature(TestDocument::point, "10km", listOf(14.0, 52.0))
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p2"
    }

    @Test
    fun shouldRankFeature() = coRun {
        val index = testDocumentIndex()
        client.bulk(target = index, refresh = Refresh.WaitFor) {
            create(
                TestDocument(
                    name = "p1",
                    feature = 20
                )
            )
            create(
                TestDocument(
                    name = "p2",
                    feature = 100
                )
            )
        }

        client.search(target = index) {
            query = rankFeature(TestDocument::feature)
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p2"
        client.search(target = index) {
            query = rankFeature(TestDocument::feature) {
                linear()
            }
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p2"
        client.search(target = index) {
            query = rankFeature(TestDocument::feature) {
                saturation(pivot = 2.0)
            }
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p2"
        client.search(target = index) {
            query = rankFeature(TestDocument::feature) {
                log(2.0)
            }
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p2"
        client.search(target = index) {
            query = rankFeature(TestDocument::feature) {
                sigmoid(pivot=2.0,exponent = 0.8)
            }
        }.hits?.hits?.first()?.parseHit<TestDocument>()!!.name shouldBe "p2"

    }
}