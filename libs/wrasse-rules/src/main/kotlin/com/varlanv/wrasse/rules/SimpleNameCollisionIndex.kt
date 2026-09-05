package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage

/**
 * Simple-name → FQNs index built from a file's whole resolved usage (every [WCallableUsage] and
 * classifier FQN). Shared by [WildcardExpansionDecision] and [QualifiedUsageDecision], both of
 * which need to know whether a simple name resolves to more than one FQN anywhere in the file's
 * usage. Compiler-free, unit-testable without kotlinc.
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
                fqnsBySimpleName
                    .getOrPut(callable.name) { mutableSetOf() }
                    .add("${callable.packageFqName}.${callable.name}")
            } else {
                fqnsBySimpleName.getOrPut(classFqName.substringAfterLast('.')) { mutableSetOf() }.add(classFqName)
            }
        }
        return fqnsBySimpleName
    }

    fun collidesWithOtherFqn(
        fqn: String,
        simpleName: String,
        index: Map<String, Set<String>>,
    ): Boolean = index[simpleName].orEmpty().any { it != fqn }
}
