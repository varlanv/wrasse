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
 * Style parameters for the opinionated printer (D21). Only [indentWidth] is consumed by the
 * Phase C.1 foundation slice; the remaining fields are the locked surface for later Phase C work.
 */
class FormatStyle(
    val indentWidth: Int = 4,
    val maxLineLength: Int = 140,
    val trailingCommas: Boolean = true,
    val importLayout: ImportLayout = ImportLayout.ASCII,
    val multilineSignatureThreshold: Int = 1,
)

enum class ImportLayout {
    ASCII,
}
