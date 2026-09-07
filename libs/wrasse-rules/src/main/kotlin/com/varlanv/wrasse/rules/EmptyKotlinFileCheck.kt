package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType

/**
 * A `FILE`'s own direct children, aside from its `PACKAGE_DIRECTIVE`, are nothing but whitespace:
 * no import, no declaration, not even a comment. Shared by [EmptyKotlinFileRule] and
 * [MissingPackageDeclarationRule], so the latter can stand down on a file the former already
 * claims.
 */
object EmptyKotlinFileCheck {
    fun isEmpty(ctx: WContext, children: ChildBuffer): Boolean {
        for (i in 0 until children.size) {
            if (children.type(i) == WNodeType.PACKAGE_DIRECTIVE) continue
            if (children.textSpan(i, ctx.sourceText).isNotBlank()) return false
        }
        return true
    }
}
