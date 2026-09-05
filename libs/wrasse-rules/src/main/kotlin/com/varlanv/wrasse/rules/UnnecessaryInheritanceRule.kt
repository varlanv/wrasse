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
 * redundant supertype. Report-only.
 *
 * Matched purely as literal text on the `SUPER_TYPE_CALL_ENTRY`'s own span — no semantic
 * resolution: `kotlin.Any()`, `Any ()` (whitespace inside the call), and a type-aliased supertype
 * are all never candidates, since none of their spans spell the bare literal exactly.
 *
 * `SUPER_TYPE_LIST` is targeted directly regardless of what declares it, covering class, object
 * (named, companion, and anonymous-literal), and enum-class declarations uniformly; an interface
 * or an enum class explicitly extending `Any()`/`Object()` never compiles in the first place
 * (`Any`/`Object` are classes, and only interfaces may appear in either declaration's supertype
 * list), so no exclusion logic is needed for either shape.
 */
class UnnecessaryInheritanceRule : WUninitializedRule {
    override val id: String = "unnecessary-inheritance"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.SUPER_TYPE_LIST)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                for (i in 0 until children.size) {
                    val type = children.type(i)
                    if (type == WNodeType.COMMA || type.isWhitespaceOrComment) continue
                    if (type != WNodeType.SUPER_TYPE_CALL_ENTRY) continue
                    val targetName = redundantTargetName(children.textSpan(i, ctx.sourceText)) ?: continue

                    reporter.report(
                        ruleId,
                        "Unnecessary inheritance of '$targetName'",
                        children.startOffset(i),
                        children.endOffset(i),
                        this,
                    )
                }
            }

            private fun redundantTargetName(entryText: CharSequence): String? = when {
                entryText.contentEquals("Any()") -> "Any"
                entryText.contentEquals("Object()") -> "Object"
                else -> null
            }
        }
    }
}
