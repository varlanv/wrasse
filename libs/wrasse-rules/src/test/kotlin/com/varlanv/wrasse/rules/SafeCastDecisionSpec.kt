package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SafeCastDecisionSpec : BaseSpec({

    fun decide(
        identifier: String = "number",
        negated: Boolean = false,
        thenText: String = "number",
        elseText: String = "null",
        typeText: String = "Int",
        replaceStart: Int = 0,
        replaceEnd: Int = 30,
        hasComment: Boolean = false,
    ) = SafeCastDecision.decide(identifier, negated, thenText, elseText, typeText, replaceStart, replaceEnd, hasComment)

    should("report and rewrite a non-negated is-check to a safe cast") {
        val verdict = decide(replaceStart = 4, replaceEnd = 34)

        verdict shouldNotBe null
        verdict!!.edits.size shouldBe 1
        val edit = verdict.edits.single()
        edit.startOffset shouldBe 4
        edit.endOffset shouldBe 34
        edit.replacement shouldBe "number as? Int"
    }

    should("report and rewrite a negated is-check to a safe cast") {
        val verdict = decide(negated = true, thenText = "null", elseText = "number")!!

        verdict.edits.single().replacement shouldBe "number as? Int"
    }

    should("splice the rewrite into a real source string") {
        val source = "if (number is Int) number else null"

        val verdict = decide(replaceStart = 0, replaceEnd = source.length)!!
        val edit = verdict.edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)

        fixed shouldBe "number as? Int"
    }

    should("not report a non-negated is-check whose then branch is not the identifier") {
        decide(thenText = "number.toString()") shouldBe null
    }

    should("not report a non-negated is-check whose else branch is not null") {
        decide(elseText = "5") shouldBe null
    }

    should("not report a negated is-check whose then branch is not null") {
        decide(negated = true, thenText = "number", elseText = "number") shouldBe null
    }

    should("report but decline the fix when a comment sits inside the matched span") {
        val verdict = decide(hasComment = true)

        verdict shouldNotBe null
        verdict!!.edits shouldBe emptyList()
    }
})
