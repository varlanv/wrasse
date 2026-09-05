package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WCallArgument
import com.varlanv.wrasse.model.WCallSite

/**
 * Pure verdict logic for `named-arguments`, compiler-free.
 *
 * [callsInScope] returns the [WCallSite.callEndOffset] of every call whose positional arguments
 * should be named: with [allCalls], every call; otherwise every call that has an argument which
 * is itself a call with arguments, plus — transitively — each such nested call. A callee declaring
 * fewer than [threshold] parameters is never in scope, though its nested calls still are.
 *
 * [isExcludedCallee] is true for a callee whose parameter names must not be written at the call
 * site: one without stable parameter names (a Java method), one in a package listed in
 * [excludedPackages] (matched by package prefix, whole segments), or a function type's own
 * `invoke`.
 *
 * [nameEdits] inserts `name = ` before each written argument of [site] that is not yet named and
 * whose value FIR mapped to a non-vararg parameter; empty when nothing is left to name. A trailing
 * lambda is never among the written arguments (it sits outside the parenthesized list).
 *
 * [positionalEdits] is the reverse, for a callee below the threshold: it drops every `name = `
 * when the written arguments map to the callee's leading parameters in declaration order and none
 * is a vararg — the only case in which positional form means the same call; empty when nothing is
 * named or the order differs.
 *
 * [isNamedArgument] recognizes an argument written as `name = value` (an identifier, plain or
 * backticked, then `=` that is not `==`). [isMixed] is true when a list holds both a named
 * argument and a positional one that is not an element of a vararg parameter — such an element
 * cannot be named, so a call naming everything else is not mixed.
 */
object NamedArgumentsDecision {
    fun callsInScope(
        callSites: List<WCallSite>,
        allCalls: Boolean,
        threshold: Int,
    ): Set<Int> {
        val inScope = HashSet<Int>()
        if (allCalls) {
            for (site in callSites) if (site.parameterCount >= threshold) inScope.add(site.callEndOffset)
            return inScope
        }
        val byCallSpan = HashMap<Long, WCallSite>(callSites.size)
        for (site in callSites) byCallSpan[spanKey(site.callStartOffset, site.callEndOffset)] = site
        val visited = HashSet<Int>()
        val pending = ArrayDeque<WCallSite>()
        for (site in callSites) {
            if (nestedCallArguments(site, byCallSpan).isNotEmpty() && visited.add(site.callEndOffset)) {
                pending.addLast(site)
            }
        }
        while (pending.isNotEmpty()) {
            val site = pending.removeFirst()
            if (site.parameterCount >= threshold) inScope.add(site.callEndOffset)
            for (nested in nestedCallArguments(site, byCallSpan)) {
                if (visited.add(nested.callEndOffset)) pending.addLast(nested)
            }
        }
        return inScope
    }

    private fun nestedCallArguments(site: WCallSite, byCallSpan: Map<Long, WCallSite>): List<WCallSite> {
        var result: MutableList<WCallSite>? = null
        for (argument in site.arguments) {
            val nested = byCallSpan[spanKey(argument.startOffset, argument.endOffset)] ?: continue
            if (nested.arguments.isEmpty()) continue
            if (result == null) result = ArrayList(2)
            result.add(nested)
        }
        return result ?: emptyList()
    }

    private fun spanKey(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFF_FFF_FFFL)

    fun isExcludedCallee(site: WCallSite, excludedPackages: List<String>): Boolean {
        if (!site.hasStableParameterNames) return true
        if (site.calleeName == "invoke" && site.calleeClassFqName?.let { isFunctionTypeClass(it) } == true) return true
        for (prefix in excludedPackages) {
            if (site.calleePackageFqName == prefix || site.calleePackageFqName.startsWith("$prefix.")) return true
        }
        return false
    }

    private fun isFunctionTypeClass(classFqName: String): Boolean =
        classFqName.startsWith(
            "kotlin.Function",
        ) ||
            classFqName.startsWith("kotlin.jvm.functions.Function") ||
            classFqName.startsWith("kotlin.coroutines.SuspendFunction") ||
            classFqName.startsWith("kotlin.reflect.KFunction")

    /** The argument FIR mapped inside the written span `[start, end)`, or null when none was. */
    fun mappedArgument(
        site: WCallSite,
        start: Int,
        end: Int,
    ): WCallArgument? = site.arguments.firstOrNull { it.startOffset >= start && it.endOffset <= end }

    fun positionalEdits(site: WCallSite, written: List<WrittenArgument>): List<WEdit> {
        var edits: MutableList<WEdit>? = null
        for ((position, argument) in written.withIndex()) {
            val mapped = mappedArgument(site, argument.startOffset, argument.endOffset) ?: return emptyList()
            if (mapped.isVararg || mapped.parameterIndex != position) return emptyList()
            if (!argument.isNamed) continue
            if (edits == null) edits = ArrayList(written.size)
            edits.add(WEdit(argument.startOffset, mapped.startOffset, ""))
        }
        return edits ?: emptyList()
    }

    fun isMixed(site: WCallSite, written: List<WrittenArgument>): Boolean {
        var named = false
        var positional = false
        for (argument in written) {
            if (argument.isNamed) {
                named = true
            } else if (mappedArgument(site, argument.startOffset, argument.endOffset)?.isVararg != true) {
                positional = true
            }
        }
        return named && positional
    }

    fun isNamedArgument(
        sourceText: CharSequence,
        start: Int,
        end: Int,
    ): Boolean {
        var i = start
        while (i < end && sourceText[i].isWhitespace()) i++
        if (i >= end) return false
        if (sourceText[i] == '`') {
            i++
            while (i < end && sourceText[i] != '`') i++
            if (i >= end) return false
            i++
        } else {
            if (!sourceText[i].isJavaIdentifierStart()) return false
            while (i < end && sourceText[i].isJavaIdentifierPart()) i++
        }
        while (i < end && sourceText[i].isWhitespace()) i++
        if (i >= end || sourceText[i] != '=') return false
        return i + 1 >= end || sourceText[i + 1] != '='
    }

    fun nameEdits(site: WCallSite, written: List<WrittenArgument>): List<WEdit> {
        var edits: MutableList<WEdit>? = null
        for (argument in written) {
            if (argument.isNamed) continue
            val mapped = mappedArgument(site, argument.startOffset, argument.endOffset) ?: continue
            if (mapped.isVararg) continue
            if (edits == null) edits = ArrayList(written.size)
            edits.add(WEdit(argument.startOffset, argument.startOffset, "${mapped.parameterName} = "))
        }
        return edits ?: emptyList()
    }
}

/** One argument as written inside a call's parentheses: its span and whether it already carries a `name =`. */
class WrittenArgument(
    val startOffset: Int,
    val endOffset: Int,
    val isNamed: Boolean,
)
