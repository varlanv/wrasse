package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.WCallSite

/**
 * The resolved calls of one file keyed by where the call expression ends. A call whose end offset
 * is shared with another resolved call is left out.
 */
internal class VarargCallIndex private constructor(private val byCallEnd: Map<Int, WCallSite>) {
    /** The resolved call whose expression ends at [callEnd], if exactly one does. */
    fun callEndingAt(callEnd: Int): WCallSite? = byCallEnd[callEnd]

    /**
     * Whether the argument list spanning [listStart]..[listEnd], with [argumentCount] arguments, is
     * the whole argument list of a call whose callee declares exactly one value parameter, a
     * `vararg`, and which passes at least two unnamed elements to it: nothing follows the list in
     * the call, and FIR mapped exactly those arguments.
     */
    fun matches(
        listStart: Int,
        listEnd: Int,
        argumentCount: Int,
    ): Boolean {
        val site = byCallEnd[listEnd] ?: return false
        return isVarargOnly(site) &&
            site.arguments.size == argumentCount &&
            site.arguments.all { it.startOffset >= listStart && it.endOffset <= listEnd }
    }

    companion object {
        val EMPTY = VarargCallIndex(emptyMap())

        fun build(callSites: List<WCallSite>): VarargCallIndex {
            if (callSites.isEmpty()) return EMPTY
            val byCallEnd = HashMap<Int, WCallSite>()
            val shared = HashSet<Int>()
            for (site in callSites) {
                if (byCallEnd.put(site.callEndOffset, site) != null) shared.add(site.callEndOffset)
            }
            shared.forEach { byCallEnd.remove(it) }
            return VarargCallIndex(byCallEnd)
        }

        private fun isVarargOnly(site: WCallSite): Boolean =
            site.hasOnlyVarargParameter && site.arguments.size >= 2 && site.arguments.all { it.isVararg && !it.isNamed }
    }
}
