package com.varlanv.wrasse.model

/** An immutable set of [WNodeType]s with O(1) ordinal-indexed membership, for hot-path `in` checks. */
class WNodeTypeSet private constructor(private val members: BooleanArray) {
    operator fun contains(type: WNodeType?): Boolean = type != null && members[type.ordinal]

    companion object {
        fun containing(vararg types: WNodeType): WNodeTypeSet {
            val members = BooleanArray(WNodeType.SIZE)
            for (type in types) members[type.ordinal] = true
            return WNodeTypeSet(members)
        }
    }
}
