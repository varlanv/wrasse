package com.varlanv.wrasse.model

/**
 * One `FirResolvedQualifier`/`FirResolvedTypeRef` whose source is a real (non-desugared)
 * LightTree span, recorded honestly as FIR sees it — not filtered by whether the span was
 * actually written qualified in source (that judgment needs walk-side syntax, a later consumer's
 * job). [startOffset]/[endOffset] are the same LightTree offsets the compiler-plugin's own SAX
 * walk reports for the identical span.
 *
 * [packageFqName] is `ClassId.packageFqName` for [targetFqName]'s class — the real package
 * boundary from FIR, never inferred from splitting the dotted [targetFqName] string (which
 * cannot distinguish a package segment from a nested-class segment, e.g. `a.b.C.Nested`). Empty
 * string for a root-package class.
 */
class WQualifiedUsage(
    val startOffset: Int,
    val endOffset: Int,
    val targetFqName: String,
    val packageFqName: String,
    val kind: WQualifiedUsageKind,
)

/**
 * Which FIR shape a [WQualifiedUsage] was recorded from.
 */
enum class WQualifiedUsageKind {
    /** A `FirResolvedQualifier` — the receiver chain of a qualified member/call access. */
    QUALIFIER,

    /** A `FirResolvedTypeRef` whose cone type is class-like. */
    TYPE_REF,
}
