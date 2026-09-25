package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.StringSlice

import com.varlanv.wrasse.model.WCallableUsage

/**
 * One explicit import directive assembled from the leaf stream: its fully-qualified
 * target, simple name, alias (if any), and the directive's own span for reporting.
 */
class ImportRecord(
    val fqn: String,
    val simpleName: String,
    val aliasName: String?,
    val startOffset: Int,
    val endOffset: Int,
)

object UnusedImportDecision {
    fun isUnused(
        import: ImportRecord,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        sourceText: CharSequence,
        kdocSpans: List<IntRange>,
    ): Boolean {
        if (matchesClassifier(import.fqn, classifiers)) return false
        if (matchesCallable(import.fqn, callables)) return false
        if (mentionedInKdoc(import.aliasName ?: import.simpleName, sourceText, kdocSpans)) return false
        return true
    }

    private fun matchesClassifier(fqn: String, classifiers: Set<String>): Boolean {
        val nestedPrefix = "$fqn."
        return classifiers.any { it == fqn || it.startsWith(nestedPrefix) }
    }

    private fun matchesCallable(fqn: String, callables: Set<WCallableUsage>): Boolean {
        val nestedPrefix = "$fqn."
        val (parent, simpleName) = splitFqn(fqn)
        return callables.any { callable ->
            val classFqName = callable.classFqName
            when {
                classFqName == null -> callable.packageFqName == parent && callable.name == simpleName
                classFqName == fqn -> true
                classFqName == parent -> callable.name == simpleName
                else -> classFqName.startsWith(nestedPrefix)
            }
        }
    }

    private fun mentionedInKdoc(
        name: String,
        sourceText: CharSequence,
        kdocSpans: List<IntRange>,
    ): Boolean {
        return kdocSpans.any { span ->
            span.first >= 0 && span.first + 2 <= span.last && span.last < sourceText.length &&
                sourceText[span.first] == '/' && sourceText[span.first + 1] == '*' && sourceText[span.first + 2] == '*' &&
                WordScan.containsWord(StringSlice(sourceText, span.first, span.last + 1), name)
        }
    }

    private fun splitFqn(fqn: String): Pair<String, String> {
        val dot = fqn.lastIndexOf('.')
        return if (dot < 0) "" to fqn else fqn.substring(0, dot) to fqn.substring(dot + 1)
    }
}
