package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * A function's own `: Unit` return type is redundant — Kotlin already infers `Unit` for a
 * function with no declared return type — and, where deleting it stays compile-legal, autofixed
 * to remove it entirely.
 *
 * Matched purely syntactically, exactly like upstream ktlint: the return-type `TYPE_REFERENCE`'s
 * own source span must equal the literal text `"Unit"`. No semantic resolution is involved, and
 * none is needed — this single exact-text check is also what keeps the rule from ever firing on
 * `Unit?`, `kotlin.Unit`, an annotated return type (`@JvmSuppressWildcards Unit`), or a `Unit`-
 * typed parameter (whose `TYPE_REFERENCE`'s parent is `VALUE_PARAMETER`, never `FUN`, so it is
 * never even a candidate) — all empirically verified never to match upstream ktlint's own rule
 * either. An expression-body function (`fun f(): Unit = expr`) is bailed entirely — not reported,
 * not fixed — matching upstream ktlint's own current (stable, shipped) scope, which explicitly
 * does not flag that shape; only a block-body function (return type's next significant sibling is
 * a `BLOCK`) is in scope.
 *
 * Reported but never autofixed when a comment sits between the colon and `Unit`, or between
 * `Unit` and the block: deleting the whitespace around a comment is not provably safe in every
 * such shape — an `EOL_COMMENT` immediately before `Unit`, for one, would swallow the following
 * `{` onto the comment's own line, corrupting the file (verified against a real compile of that
 * exact shape). Upstream ktlint autocorrects some but not all of these shapes; wrasse never
 * autofixes any of them, staying a subset of upstream rather than replicating that risk.
 */
class NoUnitReturnRule : WUninitializedRule {
    override val id: String = "no-unit-return"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.FUN)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val colonIndex = children.firstChildOfType(WNodeType.COLON)
                if (colonIndex < 0) return

                val typeReferenceIndex = nextSignificant(children, colonIndex + 1)
                if (typeReferenceIndex < 0 || children.type(typeReferenceIndex) != WNodeType.TYPE_REFERENCE) return
                if (!children.textSpan(typeReferenceIndex, ctx.sourceText).contentEquals("Unit")) return

                val bodyIndex = nextSignificant(children, typeReferenceIndex + 1)
                if (bodyIndex < 0 || children.type(bodyIndex) != WNodeType.BLOCK) return

                val hasComment =
                    hasComment(children, colonIndex + 1, typeReferenceIndex) ||
                        hasComment(children, typeReferenceIndex + 1, bodyIndex)

                val colonStart = children.startOffset(colonIndex)
                val typeReferenceEnd = children.endOffset(typeReferenceIndex)
                val edits =
                    if (hasComment) {
                        emptyList()
                    } else {
                        listOf(NoUnitReturnDeletionSpan.compute(colonStart, typeReferenceEnd))
                    }

                reporter.report(
                    ruleId, "Redundant Unit return type",
                    colonStart, typeReferenceEnd, this,
                    edits = edits
                )
            }

            private fun nextSignificant(children: ChildBuffer, from: Int): Int {
                var i = from
                while (i < children.size) {
                    if (!children.type(i).isWhitespaceOrComment) return i
                    i++
                }
                return -1
            }

            private fun hasComment(children: ChildBuffer, from: Int, until: Int): Boolean {
                for (i in from until until) {
                    when (children.type(i)) {
                        WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT, WNodeType.KDOC -> return true
                        else -> {}
                    }
                }
                return false
            }
        }
    }
}
