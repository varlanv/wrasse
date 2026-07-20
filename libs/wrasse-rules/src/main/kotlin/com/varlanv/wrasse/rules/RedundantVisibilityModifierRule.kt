package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * An explicit `public` visibility modifier is Kotlin's own default and, where deleting it stays
 * compile-legal, autofixed to remove it entirely.
 *
 * Ports detekt's own `RedundantVisibilityModifier` (ktlint ships no equivalent rule at all —
 * ground-truthed against both real checkouts). Scope matches detekt's own exactly, own conservatism
 * over any broader-but-provably-safe extension: only a `CLASS` (covers `class`/`interface`/`object
 * class`/`enum class`/`annotation class`, all one LightTree node type, distinguished only by which
 * keyword child is present — ground-truthed via a direct dump of the real LightTree structure), a
 * `FUN`, or a `PROPERTY` is a candidate (`MODIFIER_LIST`'s own immediate parent type). An
 * `OBJECT_DECLARATION` (a plain `object`/`companion object`, never a `KtClass` in detekt's own PSI
 * either), a `PRIMARY_CONSTRUCTOR`/`SECONDARY_CONSTRUCTOR`, a `TYPEALIAS`, and a `PROPERTY_ACCESSOR`
 * are never candidates — detekt's own rule never visits any of these PSI types, so wrasse doesn't
 * either. (Detekt's own same rule also flags a redundant `internal` on a member of a `private`/local
 * class — a separate check bundled into the same upstream id; out of scope here per the assignment's
 * own framing, "delete redundant `public`" — tracked as a possible follow-up, not built.)
 *
 * A `MODIFIER_LIST` containing `KW_OVERRIDE` anywhere is skipped entirely — no report, not just no
 * fix — matching detekt's own `isExplicitlyPublicNotOverridden` exclusion exactly: an override can
 * legally *widen* visibility from a more restrictive base member (`protected` to `public`), which is
 * never syntactically decidable without resolving the base declaration, so wrasse never even
 * considers this shape rather than risk deleting a load-bearing widening.
 *
 * Self-disables entirely under Kotlin's explicit API mode ([WrasseRuleConfig.explicitApiActive]):
 * an explicit `public` is a *required* declaration there, not redundant, and deleting it would break
 * the build.
 *
 * Reported but never autofixed whenever a comment sits anywhere in the modifier list itself, or
 * immediately follows the trailing whitespace this rule would otherwise delete — the established
 * uniform comment-bail precedent (`no-unit-return`, `modifier-order`), applied here even though no
 * corruption risk was actually found: see [RedundantVisibilityModifierDeletionSpan].
 */
class RedundantVisibilityModifierRule : WUninitializedRule {
    override val id: String = "redundant-visibility-modifier"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        if (config.explicitApiActive) {
            return object : WBufferedNodeRule {
                override val id = ruleId
                override val config = config
                override val targetTypes = emptySet<WNodeType>()

                override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {}
            }
        }

        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.MODIFIER_LIST)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val parentType = ctx.ancestors.peekType()
                if (parentType != WNodeType.CLASS && parentType != WNodeType.FUN && parentType != WNodeType.PROPERTY) {
                    return
                }

                var publicIndex = -1
                var hasComment = false
                for (i in 0 until children.size) {
                    when (children.type(i)) {
                        WNodeType.KW_PUBLIC -> publicIndex = i
                        WNodeType.KW_OVERRIDE -> return
                        WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT, WNodeType.KDOC -> hasComment = true
                        else -> {}
                    }
                }
                if (publicIndex < 0) return

                val publicStart = children.startOffset(publicIndex)
                val publicEnd = children.endOffset(publicIndex)
                val edit = RedundantVisibilityModifierDeletionSpan.compute(ctx.sourceText, publicStart, publicEnd, hasComment)

                reporter.report(
                    ruleId, "Redundant public visibility modifier",
                    publicStart, publicEnd, this,
                    edits = edit?.let { listOf(it) } ?: emptyList(),
                )
            }
        }
    }
}
