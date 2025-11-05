package com.jillesvangurp.ktsearch.alert.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleFiringCondition {
    abstract fun shouldTrigger(matchCount: Int): Boolean

    @Serializable
    @SerialName("AtLeast")
    data class AtLeast(val minimumMatches: Int) : RuleFiringCondition() {
        init {
            require(minimumMatches > 0) { "Minimum matches must be greater than 0" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount < minimumMatches
    }

    @Serializable
    @SerialName("AtMost")
    data class AtMost(val maximumMatches: Int) : RuleFiringCondition() {
        init {
            require(maximumMatches >= 0) { "Maximum matches must be zero or positive" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount > maximumMatches
    }
}
