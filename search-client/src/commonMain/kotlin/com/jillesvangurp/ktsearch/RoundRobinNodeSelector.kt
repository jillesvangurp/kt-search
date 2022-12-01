package com.jillesvangurp.ktsearch

interface IndexProvider {
    fun get(): Int
    fun set(value: Int)
}
expect fun indexProvider(initialIndex: Int): IndexProvider

class RoundRobinNodeSelector(
    private val nodes: Array<out Node>
) : NodeSelector {
    private val index = indexProvider(0)
    override fun selectNode(): Node {
        val result = nodes[index.get()]
        index.set((index.get() + 1).mod(nodes.size))
        return result
    }
}
