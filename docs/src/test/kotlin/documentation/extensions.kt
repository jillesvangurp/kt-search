package documentation

import com.jillesvangurp.kotlin4example.ExampleOutput
import com.jillesvangurp.kotlin4example.Kotlin4Example

context(Kotlin4Example)
fun ExampleOutput<*>.printStdOut() {
    +"""
        This prints:
    """.trimIndent()

    mdCodeBlock(stdOut, type = "text", wrap = true)
}
