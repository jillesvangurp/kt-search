package com.jillesvangurp.ktsearch.cli.command.top

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.TopKey
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterTopApiSnapshot
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterTopRenderer
import com.jillesvangurp.ktsearch.cli.command.cluster.ansiClear
import com.jillesvangurp.ktsearch.cli.command.cluster.toTopSnapshot
import com.jillesvangurp.ktsearch.cli.platformConsumeTopKey
import com.jillesvangurp.ktsearch.cli.platformDisableSingleKeyInput
import com.jillesvangurp.ktsearch.cli.platformEnableSingleKeyInput
import com.jillesvangurp.ktsearch.cli.platformIsInteractiveInput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class TopCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "top") {
    override fun help(context: Context): String =
        "Live cluster/node vitals dashboard. q quit, h/? help, Esc back."

    private val intervalSeconds by option(
        "--interval-seconds",
        help = "Polling interval seconds.",
    ).int().default(3)

    private val samples by option(
        "--samples",
        help = "Number of refresh cycles. 0 means infinite.",
    ).int().default(0)

    private val noColor by option(
        "--no-color",
        help = "Disable colored output.",
    ).flag(default = false)

    override suspend fun run() {
        if (intervalSeconds < 1) {
            currentContext.fail("--interval-seconds must be >= 1")
        }
        if (samples < 0) {
            currentContext.fail("--samples must be >= 0")
        }
        coroutineScope {
            val connectionOptions = currentContext.findObject<ConnectionOptions>()
                ?: error("Missing connection options in command context")
            val interactive = platformIsInteractiveInput()
            val renderer = ClusterTopRenderer(
                useColor = interactive && !noColor,
            )
            var renderedSamples = 0
            var previousApi: ClusterTopApiSnapshot? = null
            val quitRequested = MutableStateFlow(false)
            val helpVisible = MutableStateFlow(false)
            if (interactive) {
                platformEnableSingleKeyInput()
            }
            try {
                while (samples == 0 || renderedSamples < samples) {
                    if (interactive) {
                        consumeTopKeys(quitRequested, helpVisible)
                    }
                    if (quitRequested.value) {
                        break
                    }
                    if (helpVisible.value) {
                        if (interactive) {
                            print(ansiClear())
                        }
                        print(renderer.renderHelp())
                        print("\n")
                    } else {
                        val currentApi = service.fetchClusterTopSnapshot(
                            connectionOptions,
                        )
                        val snapshot = currentApi.toTopSnapshot(previousApi)
                        if (interactive) {
                            print(ansiClear())
                        }
                        print(renderer.render(snapshot, intervalSeconds))
                        print("\n")
                        renderedSamples++
                        previousApi = currentApi
                        if (samples > 0 && renderedSamples >= samples) {
                            break
                        }
                    }
                    if (waitForNextRefreshOrQuit(
                            intervalSeconds = intervalSeconds,
                            interactive = interactive,
                            quitRequested = quitRequested,
                            helpVisible = helpVisible,
                        )
                    ) {
                        break
                    }
                }
            } finally {
                if (interactive) {
                    platformDisableSingleKeyInput()
                }
            }
        }
    }
}

private suspend fun waitForNextRefreshOrQuit(
    intervalSeconds: Int,
    interactive: Boolean,
    quitRequested: MutableStateFlow<Boolean>,
    helpVisible: MutableStateFlow<Boolean>,
): Boolean {
    val totalWait: Duration = intervalSeconds.seconds
    var waited: Duration = Duration.ZERO
    val initialHelpVisible = helpVisible.value
    while (waited < totalWait) {
        if (interactive) {
            consumeTopKeys(quitRequested, helpVisible)
        }
        if (quitRequested.value) {
            return true
        }
        if (helpVisible.value != initialHelpVisible) {
            return false
        }
        val remaining = totalWait - waited
        val step = minOf(2.milliseconds, remaining)
        delay(step)
        waited += step
    }
    return false
}

private fun consumeTopKeys(
    quitRequested: MutableStateFlow<Boolean>,
    helpVisible: MutableStateFlow<Boolean>,
) {
    repeat(16) {
        when (platformConsumeTopKey()) {
            TopKey.Quit -> {
                quitRequested.value = true
                return
            }
            TopKey.Help -> helpVisible.value = true
            TopKey.Escape -> helpVisible.value = false
            null -> return
        }
    }
}
