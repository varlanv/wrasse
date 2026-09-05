package com.varlanv.wrasse.model

/**
 * The printer's whole option surface (D21, locked): `format` is a single on/off switch plus
 * these five style parameters — no per-rule format toggles, no code-style meta-knob. [enabled]
 * and [style] come from the `"format"` key in `wrasse.json`; [ruleConfig] is a synthetic
 * [WrasseRuleConfig] built once alongside every real rule's config (same `warnOnly` downgrade,
 * see [WConfig]) so the printer can report through the same [WReporter] path as any [WRule].
 */
class WFormatConfig(
    val enabled: Boolean,
    val style: FormatStyle,
    val ruleConfig: WrasseRuleConfig,
)

/**
 * Style parameters for the opinionated printer — the whole configurable surface.
 * [multilineSignatureThreshold] is the parameter count at or above which a function or
 * primary-constructor signature is forced one-parameter-per-line; `null` means signatures wrap
 * only when they exceed [maxLineLength].
 */
class FormatStyle(
    val indentWidth: Int = 4,
    val maxLineLength: Int = 120,
    val trailingCommas: Boolean = true,
    val importLayout: ImportLayout = ImportLayout.ASCII,
    val multilineSignatureThreshold: Int? = 3,
)

enum class ImportLayout {
    ASCII,
}
