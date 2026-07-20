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
 * A supertype-list entry whose own source span is the literal text `"Any()"` or `"Object()"` is a
 * redundant supertype and, where deleting it stays provably safe, autofixed to remove it entirely.
 *
 * Matched purely as literal text on the `SUPER_TYPE_CALL_ENTRY`'s own span — no semantic
 * resolution — the same naive check detekt's own `UnnecessaryInheritance` uses (ground-truthed
 * directly against its real engine): `kotlin.Any()`, `Any ()` (whitespace inside the call), and a
 * type-aliased supertype are all never candidates, since none of their spans spell the bare
 * literal exactly. This also means the rule structurally never touches a qualified span, so it
 * can never share a deletion region with `no-unnecessary-fqn` (which only ever edits a qualified
 * prefix) — the two ids are disjoint by construction, not by a runtime check.
 *
 * `SUPER_TYPE_LIST` is targeted directly regardless of what declares it, covering class, object
 * (named, companion, and anonymous-literal), and enum-class declarations uniformly; an interface
 * or an enum class explicitly extending `Any()`/`Object()` never compiles in the first place
 * (`Any`/`Object` are classes, and only interfaces may appear in either declaration's supertype
 * list), so no exclusion logic is needed for either shape.
 */
class UnnecessaryInheritanceRule : WUninitializedRule {
    override val id: String = "unnecessary-inheritance"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.SUPER_TYPE_LIST)

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                val entryIndices = ArrayList<Int>(children.size)
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.COMMA || type.isWhitespaceOrComment) continue
                    entryIndices.add(i)
                }

                for ((position, index) in entryIndices.withIndex()) {
                    if (children.type(index) != WNodeType.SUPER_TYPE_CALL_ENTRY) continue
                    val targetName = redundantTargetName(children.textSpan(index, ctx.sourceText)) ?: continue

                    val entryStart = children.startOffset(index)
                    val entryEnd = children.endOffset(index)
                    val edit = when {
                        entryIndices.size == 1 ->
                            UnnecessaryInheritanceDeletionSpan.computeSoleEntry(ctx.sourceText, entryStart, entryEnd)

                        position == entryIndices.size - 1 -> {
                            val previousIndex = entryIndices[position - 1]
                            UnnecessaryInheritanceDeletionSpan.computeTrailingEntry(
                                previousEntryEnd = children.endOffset(previousIndex),
                                entryEnd = entryEnd,
                                hasAdjacentComment = hasComment(children, previousIndex + 1, index),
                            )
                        }

                        else -> {
                            val nextIndex = entryIndices[position + 1]
                            UnnecessaryInheritanceDeletionSpan.computeLeadingEntry(
                                entryStart = entryStart,
                                nextEntryStart = children.startOffset(nextIndex),
                                hasAdjacentComment = hasComment(children, index + 1, nextIndex),
                            )
                        }
                    }

                    reporter.report(
                        ruleId, "Unnecessary inheritance of '$targetName'",
                        entryStart, entryEnd, this,
                        edits = edit?.let { listOf(it) } ?: emptyList(),
                    )
                }
            }

            private fun redundantTargetName(entryText: CharSequence): String? = when {
                entryText.contentEquals("Any()") -> "Any"
                entryText.contentEquals("Object()") -> "Object"
                else -> null
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
