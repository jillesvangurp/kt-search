package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.ktsearch.ClusterStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleCheck {
    abstract val targetDescription: String

    @Serializable
    @SerialName("Search")
    data class Search(
        val target: String,
        val queryJson: String
    ) : RuleCheck() {
        override val targetDescription: String get() = target
    }

    @Serializable
    @SerialName("ClusterStatus")
    data class ClusterStatusCheck(
        val expectedStatus: ClusterStatus = ClusterStatus.Green,
        val description: String = "cluster"
    ) : RuleCheck() {
        override val targetDescription: String get() = description
    }
}
