package com.jillesvangurp.ktsearch.alert.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleFiringCondition {
    abstract fun shouldTrigger(matchCount: Int): Boolean
    internal abstract fun describeCondition(): String

    @Serializable
    @SerialName("Equals")
    data class Equals(val expectedMatches: Int) : RuleFiringCondition() {
        init {
            require(expectedMatches >= 0) { "Expected matches must be zero or positive" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount == expectedMatches
        override fun describeCondition(): String = "count == $expectedMatches"
    }

    @Serializable
    @SerialName("LessThan")
    data class LessThan(val threshold: Int) : RuleFiringCondition() {
        init {
            require(threshold > 0) { "Threshold must be greater than 0" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount < threshold
        override fun describeCondition(): String = "count < $threshold"
    }

    @Serializable
    @SerialName("LessThanOrEqual")
    data class LessThanOrEqual(val threshold: Int) : RuleFiringCondition() {
        init {
            require(threshold >= 0) { "Threshold must be zero or positive" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount <= threshold
        override fun describeCondition(): String = "count <= $threshold"
    }

    @Serializable
    @SerialName("GreaterThan")
    data class GreaterThan(val threshold: Int) : RuleFiringCondition() {
        init {
            require(threshold >= 0) { "Threshold must be zero or positive" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount > threshold
        override fun describeCondition(): String = "count > $threshold"
    }

    @Serializable
    @SerialName("GreaterThanOrEqual")
    data class GreaterThanOrEqual(val threshold: Int) : RuleFiringCondition() {
        init {
            require(threshold >= 0) { "Threshold must be zero or positive" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount >= threshold
        override fun describeCondition(): String = "count >= $threshold"
    }

    @Deprecated(
        message = "Use LessThan instead",
        replaceWith = ReplaceWith("RuleFiringCondition.LessThan(minimumMatches)")
    )
    @Serializable
    @SerialName("AtLeast")
    data class AtLeast(val minimumMatches: Int) : RuleFiringCondition() {
        init {
            require(minimumMatches > 0) { "Minimum matches must be greater than 0" }
        }

        private val delegate = LessThan(minimumMatches)

        override fun shouldTrigger(matchCount: Int): Boolean = delegate.shouldTrigger(matchCount)
        override fun describeCondition(): String = delegate.describeCondition()
    }

    @Deprecated(
        message = "Use GreaterThan instead",
        replaceWith = ReplaceWith("RuleFiringCondition.GreaterThan(maximumMatches)")
    )
    @Serializable
    @SerialName("AtMost")
    data class AtMost(val maximumMatches: Int) : RuleFiringCondition() {
        init {
            require(maximumMatches >= 0) { "Maximum matches must be zero or positive" }
        }

        private val delegate = GreaterThan(maximumMatches)

        override fun shouldTrigger(matchCount: Int): Boolean = delegate.shouldTrigger(matchCount)
        override fun describeCondition(): String = delegate.describeCondition()
    }
}
