package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * Deletion-span policy for an unused import directive, compiler-free so it is unit-testable
 * without a kotlinc dependency.
 *
 * If the directive is the only non-whitespace content on its line(s) ([ImportLineSpan.isAloneOnLine]
 * against [sourceText] between the enclosing line boundaries), the whole line is removed, including
 * its terminating `\n` (or to end-of-text if it is the file's last line with no trailing newline) —
 * otherwise the deletion would leave a blank line behind. This also covers a directive whose own
 * statement-terminating semicolon is inside its own node span (the Kotlin grammar binds it there)
 * when that directive is alone on its line: `import x.y;` alone gets whole-line removal, semicolon
 * included, same as without one.
 *
 * Otherwise — something else shares the line (a semicolon-separated sibling import, a trailing
 * comment) — this returns `null`: **no edit at all**, report-only for that directive (D9,
 * "behavior-preserving and bail when uncertain"). This is a deliberate bail, not a missing case:
 * deleting only `[startOffset, endOffset)` of the *right-hand* directive in
 * `import a.b; import c.d` leaves `import a.b; ` behind — the left directive's separator
 * semicolon is now a statement-trailing one, which `no-semicolons` did not flag before the
 * deletion and does flag after it. With both rules enabled (as this repo's own `wrasse.json`
 * does), that breaks `fix(fix(x)) == fix(x)` (D19): pass 2 emits a new `no-semicolons`
 * diagnostic+edit that pass 1 never reported. Extending the deletion backward to also eat the
 * separator would fix that but reach into the *left* directive's own span — an overlap the
 * EditPlan must reject when *both* directives on the line are unused. Composing across rules
 * here (deciding a neighbor's semicolon fate from inside `no-unused-imports`) is exactly the kind
 * of cross-rule coupling the design forbids outside of a real engine (§5.1) — so this bails
 * instead of guessing; the same-line case stays a lint-only finding until the ImportEngine fuses
 * import removal and statement-separator cleanup into one decision-maker.
 */
object ImportRemovalSpan {

    fun compute(sourceText: CharSequence, startOffset: Int, endOffset: Int): WEdit? {
        if (!ImportLineSpan.isAloneOnLine(sourceText, startOffset, endOffset)) return null
        val lineStart = ImportLineSpan.lineStartBefore(sourceText, startOffset)
        val newlineAtOrAfterEnd = ImportLineSpan.indexOfNewlineFrom(sourceText, endOffset)
        val deleteEnd = if (newlineAtOrAfterEnd >= 0) newlineAtOrAfterEnd + 1 else sourceText.length
        return WEdit(lineStart, deleteEnd, "")
    }
}
