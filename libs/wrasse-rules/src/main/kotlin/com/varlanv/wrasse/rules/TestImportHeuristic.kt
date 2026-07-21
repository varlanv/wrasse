package com.varlanv.wrasse.rules

/**
 * Whether an `import` directive's own source span names a package under one of [prefixes],
 * used to infer "this file is test code" without a test-source-set concept.
 */
object TestImportHeuristic {
    fun matches(importDirectiveText: CharSequence, prefixes: Set<String>): Boolean {
        val path = importDirectiveText.toString().removePrefix("import").trim()
        return prefixes.any { path.startsWith(it) }
    }
}
