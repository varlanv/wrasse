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
        val leafRules = ArrayList<WLeafRule>(rules.size)
        val nodeRules = ArrayList<WNodeRule>(rules.size)
        val stream = ArrayList<WStreamRule>(4)
        val file = ArrayList<WFileRule>(4)

        for (rule in rules) {
            when (rule) {
                is WLeafRule -> leafRules.add(rule)
                is WNodeRule -> nodeRules.add(rule)
                is WStreamRule -> stream.add(rule)
                is WFileRule -> file.add(rule)
            }
        }

        val none = emptyList<Nothing>()
        val ld = arrayOfNulls<ArrayList<WLeafRule>?>(WNodeType.SIZE)
        for (i in 0 until leafRules.size) {
            val rule = leafRules[i]
            for (type in rule.targetTypes) {
                val list = ld[type.ordinal] ?: ArrayList<WLeafRule>(2).also { ld[type.ordinal] = it }
                list.add(rule)
            }
        }

        val nd = arrayOfNulls<ArrayList<WNodeRule>?>(WNodeType.SIZE)
        for (i in 0 until nodeRules.size) {
            val rule = nodeRules[i]
            for (type in rule.targetTypes) {
                val list = nd[type.ordinal] ?: ArrayList<WNodeRule>(2).also { nd[type.ordinal] = it }
                list.add(rule)
            }
        }
        this.leafDispatch = Array(ld.size) { ld[it] ?: none }
        this.nodeDispatch = Array(nd.size) { nd[it] ?: none }
        this.allRules = rules
        this.streamRules = stream
        this.fileRules = file
        this.hasLeafRules = leafRules.isNotEmpty()
        this.hasNodeRules = nodeRules.isNotEmpty()
        this.hasStreamRules = stream.isNotEmpty()
    }

    fun leafRulesForType(type: WNodeType): List<WLeafRule> = leafDispatch[type.ordinal]

    fun nodeRulesForType(type: WNodeType): List<WNodeRule> = nodeDispatch[type.ordinal]
}
