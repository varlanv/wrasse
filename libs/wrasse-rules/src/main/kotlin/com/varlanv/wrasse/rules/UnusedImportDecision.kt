package com.varlanv.wrasse.rules

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

/**
 * Pure verdict logic for `no-unused-imports`, compiler-free so it is unit-testable without
 * a kotlinc dependency. Every ambiguity resolves toward "used" (bail-on-ambiguity, design.md
 * §6): a classifier or callable is matched by exact FQN or by FQN-prefix (covers nested
 * classes, constructors, companion/static-like members, and enum entries), and as a last,
 * conservative resort, the import's visible name is looked up as a whole word inside every
 * recorded comment/KDoc span of the file (KDoc references are invisible to FIR).
 */
object UnusedImportDecision {

    fun isUnused(
        import: ImportRecord,
        classifiers: Set<String>,
        callables: Set<WCallableUsage>,
        sourceText: CharSequence,
        commentSpans: List<IntRange>,
    ): Boolean {
        if (matchesClassifier(import.fqn, classifiers)) return false
        if (matchesCallable(import.fqn, callables)) return false
        if (mentionedInComments(import.aliasName ?: import.simpleName, sourceText, commentSpans)) return false
        return true
    }

    private fun matchesClassifier(fqn: String, classifiers: Set<String>): Boolean {
        val nestedPrefix = "$fqn."
        return classifiers.any { it == fqn || it.startsWith(nestedPrefix) }
    }

    private fun matchesCallable(fqn: String, callables: Set<WCallableUsage>): Boolean {
        val nestedPrefix = "$fqn."
        val (parentPackage, simpleName) = splitFqn(fqn)
        return callables.any { callable ->
            val classFqName = callable.classFqName
            when {
                classFqName == null -> callable.packageFqName == parentPackage && callable.name == simpleName
                classFqName == fqn -> true
                else -> classFqName.startsWith(nestedPrefix)
            }
        }
    }

    private fun mentionedInComments(name: String, sourceText: CharSequence, commentSpans: List<IntRange>): Boolean {
        if (commentSpans.isEmpty()) return false
        val wordPattern = Regex("\\b" + Regex.escape(name) + "\\b")
        return commentSpans.any { span -> wordPattern.containsMatchIn(sourceText.subSequence(span.first, span.last + 1)) }
    }

    private fun splitFqn(fqn: String): Pair<String, String> {
        val dot = fqn.lastIndexOf('.')
        return if (dot < 0) "" to fqn else fqn.substring(0, dot) to fqn.substring(dot + 1)
    }
}
