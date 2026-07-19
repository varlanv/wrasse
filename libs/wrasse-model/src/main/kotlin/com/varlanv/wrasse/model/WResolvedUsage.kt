package com.varlanv.wrasse.model

/**
 * File-level facade over what a file's FIR resolution already knows: every classifier and
 * callable it resolved a reference to, and whether resolution hit an error anywhere.
 *
 * Built once per file by the compiler-plugin host (Phase B.3, [WContext.resolvedUsage]) and
 * carries zero kotlinc types, so it is usable from [wrasse-rules] without a kotlinc dependency.
 */
class WResolvedUsage(
    /** Fully-qualified names (e.g. "kotlin.collections.List") of every classifier the file references, including type arguments, annotation types, and qualifier references. */
    val classifiers: Set<String>,
    /** Every resolved callable (function, property, constructor) the file references. */
    val callables: Set<WCallableUsage>,
    /** True if the file contains any unresolved reference or error type. Consumers must bail entirely on such files. */
    val hasResolutionErrors: Boolean,
)

/**
 * A single resolved callable reference: a top-level callable (incl. extensions) when
 * [classFqName] is null, otherwise a member of the class named by [classFqName].
 */
class WCallableUsage(
    /** Package containing the callable (for members, the package of the containing class). */
    val packageFqName: String,
    /** Fully-qualified name of the containing class, or null for a top-level callable. */
    val classFqName: String?,
    /** Simple name of the callable. */
    val name: String,
) {
    override fun equals(other: Any?): Boolean =
        other is WCallableUsage &&
            packageFqName == other.packageFqName &&
            classFqName == other.classFqName &&
            name == other.name

    override fun hashCode(): Int {
        var result = packageFqName.hashCode()
        result = 31 * result + (classFqName?.hashCode() ?: 0)
        result = 31 * result + name.hashCode()
        return result
    }
}
