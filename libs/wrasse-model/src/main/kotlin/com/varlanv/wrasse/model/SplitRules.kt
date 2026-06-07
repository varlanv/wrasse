package com.varlanv.wrasse.model

class SplitRules(rules: List<WRule>) {

    val fileVisitorRules: List<FileVisitorWRule>
    val hasNodeRules: Boolean
    private val nodeDispatch: Array<List<NodeVisitorWRule>>

    init {
        val fileVisitors = ArrayList<FileVisitorWRule>()
        val dispatch = Array<MutableList<NodeVisitorWRule>?>(WNodeType.entries.size) { null }

        for (rule in rules) {
            when (rule) {
                is FileVisitorWRule -> fileVisitors.add(rule)
                is NodeVisitorWRule -> {
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

    fun rulesForType(type: WNodeType): List<NodeVisitorWRule> = nodeDispatch[type.ordinal]
}
