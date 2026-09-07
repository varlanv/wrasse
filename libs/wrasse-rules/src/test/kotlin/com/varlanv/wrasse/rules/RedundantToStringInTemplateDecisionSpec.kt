package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RedundantToStringInTemplateDecisionSpec : BaseSpec({

    fun decide(
        receiverType: WNodeType = WNodeType.REFERENCE_EXPRESSION,
        selectorType: WNodeType = WNodeType.CALL_EXPRESSION,
        selectorText: CharSequence = "toString()",
        receiverText: CharSequence = "x",
        entryStart: Int = 0,
        entryEnd: Int = 20,
        dotStart: Int = 3,
        callEnd: Int = 19,
    ) = RedundantToStringInTemplateDecision.decide(
        receiverType = receiverType,
        selectorType = selectorType,
        selectorText = selectorText,
        receiverText = receiverText,
        entryStart = entryStart,
        entryEnd = entryEnd,
        dotStart = dotStart,
        callEnd = callEnd,
    )

    should("report and shorten a plain identifier receiver to \$name, replacing the whole entry") {
        val verdict = decide(receiverText = "x", entryStart = 5, entryEnd = 25)

        verdict shouldNotBe null
        verdict!!.edits.size shouldBe 1
        val edit = verdict.edits.single()
        edit.startOffset shouldBe 5
        edit.endOffset shouldBe 25
        edit.replacement shouldBe "\$x"
    }

    should("splice the shorthand into a real source string") {
        val source = "\"\${x.toString()}\""
        val entryStart = source.indexOf("\${")
        val entryEnd = source.indexOf("}") + 1

        val verdict = decide(receiverText = "x", entryStart = entryStart, entryEnd = entryEnd)!!
        val edit = verdict.edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)

        fixed shouldBe "\"\$x\""
    }

    should("report and keep the braces for a dotted-chain receiver, dropping only the call") {
        val source = "\"\${a.b.toString()}\""
        val dotStart = source.indexOf(".toString()")
        val callEnd = source.indexOf("}")

        val verdict = decide(
            receiverType = WNodeType.DOT_QUALIFIED_EXPRESSION,
            receiverText = "a.b",
            dotStart = dotStart,
            callEnd = callEnd,
        )!!
        val edit = verdict.edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)

        fixed shouldBe "\"\${a.b}\""
    }

    should("report and keep the braces for a call-expression receiver, dropping only the call") {
        val verdict = decide(receiverType = WNodeType.CALL_EXPRESSION, receiverText = "f()")

        verdict shouldNotBe null
        val edit = verdict!!.edits.single()
        edit.startOffset shouldBe 3
        edit.endOffset shouldBe 19
        edit.replacement shouldBe ""
    }

    should("report but decline the fix for a backtick-quoted identifier receiver") {
        val verdict = decide(receiverText = "`my var`")

        verdict shouldNotBe null
        val edit = verdict!!.edits.single()
        edit.replacement shouldBe ""
    }

    should("report but decline the fix for a bare this receiver") {
        val verdict = decide(receiverType = WNodeType.THIS_EXPRESSION, receiverText = "this")

        verdict shouldNotBe null
        verdict!!.edits shouldBe emptyList()
    }

    should("not report at all for super.toString()") {
        decide(receiverType = WNodeType.SUPER_EXPRESSION) shouldBe null
    }

    should("not report at all for a call with arguments") {
        decide(selectorText = "toString(radix)") shouldBe null
    }

    should("not report at all for a selector that is not a call expression") {
        decide(selectorType = WNodeType.REFERENCE_EXPRESSION) shouldBe null
    }
})
