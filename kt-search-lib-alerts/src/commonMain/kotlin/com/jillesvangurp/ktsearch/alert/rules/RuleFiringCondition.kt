package com.jillesvangurp.ktsearch.alert.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleFiringCondition {
    abstract fun shouldTrigger(matchCount: Int): Boolean

    @Serializable
    @SerialName("Default")
    data object Default : RuleFiringCondition() {
        override fun shouldTrigger(matchCount: Int): Boolean = matchCount > 0
    }

    @Serializable
    @SerialName("AtLeast")
    data class AtLeast(val minimumMatches: Int) : RuleFiringCondition() {
        init {
            require(minimumMatches > 0) { "Minimum matches must be greater than 0" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount < minimumMatches
    }

    @Serializable
    @SerialName("Max")
    data class Max(val maximumMatches: Int) : RuleFiringCondition() {
        init {
            require(maximumMatches >= 0) { "Maximum matches must be zero or positive" }
        }

        override fun shouldTrigger(matchCount: Int): Boolean = matchCount > maximumMatches
    }

    companion object {
        val LEGACY_DEFAULT: RuleFiringCondition = Default
    }
}
