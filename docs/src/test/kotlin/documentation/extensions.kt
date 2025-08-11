package documentation

import com.jillesvangurp.kotlin4example.ExampleOutput
import com.jillesvangurp.kotlin4example.Kotlin4Example

// FIXME figure out the replacement of context receivers once that stabilizes.
// For now use this slightly ugly hack
fun ExampleOutput<*>.printStdOut(kotlin4Example: Kotlin4Example) {
    kotlin4Example.apply {
        +"""
            This prints:
        """.trimIndent()
        mdCodeBlock(stdOut, type = "text", wrap = true)
    }
}
