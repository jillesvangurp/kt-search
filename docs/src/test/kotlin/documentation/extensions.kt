package documentation

import com.jillesvangurp.kotlin4example.ExampleOutput
import com.jillesvangurp.kotlin4example.Kotlin4Example

context(Kotlin4Example)
fun ExampleOutput<*>.printStdOut() {
+"""
This prints:
 
```
$stdOut
```
""".trimIndent()
}
