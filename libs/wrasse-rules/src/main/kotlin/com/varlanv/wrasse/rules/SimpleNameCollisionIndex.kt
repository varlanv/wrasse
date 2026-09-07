package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallableUsage

/**
 * Simple-name → FQNs index built from a file's whole resolved usage (every [WCallableUsage] and
 * classifier FQN). Shared by [WildcardExpansionDecision] and [QualifiedUsageDecision], both of
 * which need to know whether a simple name resolves to more than one FQN anywhere in the file's
 * usage. A type alias and the class it expands to are one FQN here ([typeAliases], alias to
 * expansion): resolving a type through an alias records both, and that is not a collision.
 * Compiler-free, unit-testable without kotlinc.
 */
object SimpleNameCollisionIndex {
    fun build(
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        typeAliases: Map<String, String> = emptyMap(),
    ): Map<String, Set<String>> {
        val fqnsBySimpleName = mutableMapOf<String, MutableSet<String>>()
        for (classifier in classifiers) {
            fqnsBySimpleName
                .getOrPut(classifier.substringAfterLast('.')) { mutableSetOf() }
                .add(typeAliases[classifier] ?: classifier)
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
        typeAliases: Map<String, String> = emptyMap(),
    ): Boolean {
        val canonical = typeAliases[fqn] ?: fqn
        return index[simpleName].orEmpty().any { it != canonical }
    }
}
