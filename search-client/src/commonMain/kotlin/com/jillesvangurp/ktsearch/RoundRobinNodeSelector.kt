package com.jillesvangurp.ktsearch

interface IndexProvider {
    fun get(): Int
    fun set(value: Int)
}
expect fun simpleIndexProvider(initialIndex: Int=0): IndexProvider

class RoundRobinNodeSelector(
    private val nodes: Array<out Node>
) : NodeSelector {
    private val index = simpleIndexProvider()
    override fun selectNode(): Node {
        val result = nodes[index.get()]
        index.set((index.get() + 1).mod(nodes.size))
        return result
    }
}
