package com.jillesvangurp.ktsearch

import kotlin.native.concurrent.ThreadLocal

class RoundRobinNodeSelector: NodeSelector {
    @ThreadLocal
    private var index: Int = 0
    override fun selectNode(initialNodes: Array<out Node>): Node {
        return initialNodes[index++].also {
            if(index > initialNodes.size-1) {
                index=0
            }
        }
    }
}