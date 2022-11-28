package com.jillesvangurp.ktsearch

import kotlin.native.concurrent.ThreadLocal

class RoundRobinNodeSelector(
    private val nodes: Array<out Node>
) : NodeSelector {
    @ThreadLocal
    private var index: Int = 0
    override fun selectNode(): Node {
        return nodes[index++].also {
            if(index > nodes.size-1) {
                index=0
            }
        }
    }
}