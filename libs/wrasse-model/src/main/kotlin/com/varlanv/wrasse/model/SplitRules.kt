package com.varlanv.wrasse.model

class SplitRules(rules: List<WRule>) {

    val fileVisitorRules: List<WFileRule>
    val hasNodeRules: Boolean
    private val nodeDispatch: Array<List<WNodeRule>>

    init {
        val fileVisitors = ArrayList<WFileRule>()
        val dispatch = Array<MutableList<WNodeRule>?>(WNodeType.entries.size) { null }

        for (rule in rules) {
            when (rule) {
                is WFileRule -> fileVisitors.add(rule)
                is WNodeRule -> {
                    for (type in rule.targetTypes) {
                        val list = dispatch[type.ordinal]
                        if (list != null) {
                            list.add(rule)
                        } else {
                            dispatch[type.ordinal] = mutableListOf(rule)
                        }
                    }
                }
            }
        }

        this.fileVisitorRules = fileVisitors
        this.nodeDispatch = Array(dispatch.size) { dispatch[it] ?: emptyList() }
        this.hasNodeRules = nodeDispatch.any { it.isNotEmpty() }
    }

    fun rulesForType(type: WNodeType): List<WNodeRule> = nodeDispatch[type.ordinal]
}
