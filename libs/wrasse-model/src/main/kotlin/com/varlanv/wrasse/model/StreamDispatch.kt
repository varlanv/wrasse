package com.varlanv.wrasse.model

/**
 * Dispatch table that routes SAX events to the appropriate rules.
 *
 * Splits rules into four categories at construction time:
 * - [WLeafRule] — dispatched by [WNodeType] ordinal (O(1) array lookup) on leaf events.
 * - [WNodeRule] / [WBufferedNodeRule] — dispatched by ordinal on interior node enter/exit.
 * - [WStreamRule] — receives every leaf event (no type filtering); used by spacing/indentation.
 * - [WFileRule] — called once after the walk with the full source text.
 */
class StreamDispatch(rules: List<WRule>) {
    private val leafDispatch: Array<List<WLeafRule>>
    private val nodeDispatch: Array<List<WNodeRule>>
    val streamRules: List<WStreamRule>
    val fileRules: List<WFileRule>
    val allRules: List<WRule>
    val hasLeafRules: Boolean
    val hasNodeRules: Boolean
    val hasStreamRules: Boolean

    init {
        val leafRules = mutableListOf<WLeafRule>()
        val nodeRules = mutableListOf<WNodeRule>()
        val stream = mutableListOf<WStreamRule>()
        val file = mutableListOf<WFileRule>()

        for (rule in rules) {
            when (rule) {
                is WLeafRule -> leafRules.add(rule)
                is WNodeRule -> nodeRules.add(rule)
                is WStreamRule -> stream.add(rule)
                is WFileRule -> file.add(rule)
            }
        }

        val ld = arrayOfNulls<MutableList<WLeafRule>?>(WNodeType.SIZE)
        for (rule in leafRules) {
            for (type in rule.targetTypes) {
                val list = ld[type.ordinal] ?: mutableListOf<WLeafRule>().also { ld[type.ordinal] = it }
                list.add(rule)
            }
        }

        val nd = arrayOfNulls<MutableList<WNodeRule>?>(WNodeType.SIZE)
        for (rule in nodeRules) {
            for (type in rule.targetTypes) {
                val list = nd[type.ordinal] ?: mutableListOf<WNodeRule>().also { nd[type.ordinal] = it }
                list.add(rule)
            }
        }
        this.leafDispatch = Array(ld.size) { ld[it] ?: emptyList() }
        this.nodeDispatch = Array(nd.size) { nd[it] ?: emptyList() }
        this.allRules = rules
        this.streamRules = stream
        this.fileRules = file
        this.hasLeafRules = leafDispatch.any { it.isNotEmpty() }
        this.hasNodeRules = nodeDispatch.any { it.isNotEmpty() }
        this.hasStreamRules = stream.isNotEmpty()
    }

    fun leafRulesForType(type: WNodeType): List<WLeafRule> = leafDispatch[type.ordinal]

    fun nodeRulesForType(type: WNodeType): List<WNodeRule> = nodeDispatch[type.ordinal]
}
