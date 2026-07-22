package com.varlanv.wrasse.rules

/**
 * Verdict logic for two extension functions sharing one signature on two related receiver
 * classes, compiler-free so it is unit-testable without a kotlinc dependency. A signature is a
 * function name plus its own parameter *names* (never their types) plus its own return type's raw
 * text — the same crude, resolution-free proxy the upstream rule this derives from relies on
 * (matching by name only sidesteps ever needing to resolve a parameter's actual type). Two classes
 * are "related" only when this file's own inheritance graph directly names one as the other's
 * supertype — never transitively, matching upstream's own single-hop check, and only within this
 * file (upstream's own known limitation, "should find all related classes in project, not only in
 * file", carried over unchanged since this project's rules see one file at a time regardless).
 * Each duplicate signature only ever pairs with the *first* occurrence seen (never with a later
 * one), so three same-signature functions on three pairwise-related classes report only two of the
 * three, matching the upstream rule's own first-occurrence-anchored pairing exactly.
 */
object ExtensionFunctionsSameNameDecision {
    data class Candidate(val receiverClassName: String, val functionName: String, val paramNames: List<String>, val returnType: String?)

    fun message(functionName: String, receiverClassName: String, otherReceiverClassName: String): String =
    "Extension function '$functionName' on '$receiverClassName' has the same signature as one on related class '$otherReceiverClassName'"

    fun indicesToReport(candidates: List<Candidate>, relatedClassPairs: List<Pair<String, String>>): Map<Int, Int> {
        val firstIndexBySignature = LinkedHashMap<List<Any?>, Int>()
        val result = mutableMapOf<Int, Int>()
        for ((index, candidate) in candidates.withIndex()) {
            val key = listOf(candidate.functionName, candidate.paramNames, candidate.returnType)
            val firstIndex = firstIndexBySignature[key]
            if (firstIndex == null) {
                firstIndexBySignature[key] = index
                continue
            }
            val first = candidates[firstIndex]
            if (areRelated(relatedClassPairs, first.receiverClassName, candidate.receiverClassName)) {
                result[firstIndex] = index
                result[index] = firstIndex
            }
        }
        return result
    }

    private fun areRelated(relatedClassPairs: List<Pair<String, String>>, classA: String, classB: String): Boolean =
    relatedClassPairs.any { (it.first == classA && it.second == classB) || (it.first == classB && it.second == classA) }
}
