package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage

/**
 * Simple-name → FQNs index built from a file's whole resolved usage (every [WCallableUsage] and
 * classifier FQN), shared by [WildcardExpansionDecision] (bail 7, star-expansion ambiguity) and
 * [QualifiedUsageDecision] (`no-unnecessary-fqn`'s import-viability check) — both need the
 * identical question answered: "does this simple name resolve to more than one FQN anywhere in
 * this file's usage?" Compiler-free, unit-testable without kotlinc.
 */
object SimpleNameCollisionIndex {

    fun build(classifiers: Set<String>, callables: Set<WCallableUsage>): Map<String, Set<String>> {
        val fqnsBySimpleName = mutableMapOf<String, MutableSet<String>>()
        for (classifier in classifiers) {
            fqnsBySimpleName.getOrPut(classifier.substringAfterLast('.')) { mutableSetOf() }.add(classifier)
        }
        for (callable in callables) {
            val classFqName = callable.classFqName
            if (classFqName == null) {
                fqnsBySimpleName.getOrPut(callable.name) { mutableSetOf() }
                    .add("${callable.packageFqName}.${callable.name}")
            } else {
                fqnsBySimpleName.getOrPut(classFqName.substringAfterLast('.')) { mutableSetOf() }.add(classFqName)
            }
        }
        return fqnsBySimpleName
    }

    fun collidesWithOtherFqn(fqn: String, simpleName: String, index: Map<String, Set<String>>): Boolean =
        index[simpleName].orEmpty().any { it != fqn }
}
