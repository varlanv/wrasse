package com.varlanv.wrasse.model

/**
 * A source file as seen by wrasse rules.
 * Holds the full CST and the original source text.
 */
class WFile(
    /** Absolute path to the source file. */
    val path: String,

    /** Root node of the CST — always type FILE. */
    val root: WNode,

    /**
     * The entire source text of the file as a single CharSequence.
     * Backed by the compiler's internal char buffer — no copy.
     * Use with node offsets to extract any subtree's text:
     *   sourceText.subSequence(node.startOffset, node.endOffset)
     */
    val sourceText: CharSequence,
)
