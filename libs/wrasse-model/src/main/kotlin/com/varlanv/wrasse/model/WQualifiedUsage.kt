package com.varlanv.wrasse.model

/**
 * One `FirResolvedQualifier`/`FirResolvedTypeRef` whose source is a real (non-desugared)
 * LightTree span, recorded honestly as FIR sees it — not filtered by whether the span was
 * actually written qualified in source (that judgment needs walk-side syntax, a later
 * consumer's job, not this facade's). [startOffset]/[endOffset] are the same LightTree offsets
 * the compiler-plugin's own SAX walk reports for the identical span (design.md §8, D.1 spike).
 */
class WQualifiedUsage(
    val startOffset: Int,
    val endOffset: Int,
    val targetFqName: String,
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
