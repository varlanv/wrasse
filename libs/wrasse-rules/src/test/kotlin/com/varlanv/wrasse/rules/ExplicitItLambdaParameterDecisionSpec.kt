package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ExplicitItLambdaParameterDecisionSpec :
    BaseSpec(
        {

            should("delete from the opening brace's end through the arrow's end for an untyped 'it'") {
                val source = "{ it -> it.plus(1) }"
                val lbraceEnd = source.indexOf("{") + 1
                val vpListStart = source.indexOf("it")
                val arrowEnd = source.indexOf("->") + 2

                val verdict = ExplicitItLambdaParameterDecision
                    .decide(vpListStart = vpListStart, lbraceEnd = lbraceEnd, arrowEnd = arrowEnd, hasType = false, hasComment = false)

                verdict.reportStart shouldBe vpListStart
                verdict.reportEnd shouldBe arrowEnd
                verdict.message shouldBe ExplicitItLambdaParameterDecision.UNTYPED_MESSAGE
                verdict.edits.size shouldBe 1
                val edit = verdict.edits.single()
                edit.startOffset shouldBe lbraceEnd
                edit.endOffset shouldBe arrowEnd
                edit.replacement shouldBe ""

                val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)
                fixed shouldBe "{ it.plus(1) }"
            }

            should("leave the arrow-to-body gap on a multiline lambda completely untouched") {
                val source = "{ it ->\n    it.plus(1)\n}"
                val lbraceEnd = source.indexOf("{") + 1
                val vpListStart = source.indexOf("it")
                val arrowEnd = source.indexOf("->") + 2

                val verdict = ExplicitItLambdaParameterDecision
                    .decide(vpListStart = vpListStart, lbraceEnd = lbraceEnd, arrowEnd = arrowEnd, hasType = false, hasComment = false)

                val edit = verdict.edits.single()
                val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)
                fixed shouldBe "{\n    it.plus(1)\n}"
            }

            should("bail with no edits, report-only, for a typed 'it' parameter") {
                val source = "{ it: Int -> it.toString() }"
                val vpListStart = source.indexOf("it")
                val lbraceEnd = vpListStart
                val arrowEnd = source.indexOf("->") + 2

                val verdict = ExplicitItLambdaParameterDecision
                    .decide(vpListStart = vpListStart, lbraceEnd = lbraceEnd, arrowEnd = arrowEnd, hasType = true, hasComment = false)

                verdict.message shouldBe ExplicitItLambdaParameterDecision.TYPED_MESSAGE
                verdict.edits shouldBe emptyList()
            }

            should("bail with no edits, report-only, when a comment sits in the header") {
                val source = "{ /* c */ it -> it.plus(1) }"
                val vpListStart = source.indexOf("it")
                val lbraceEnd = source.indexOf("/*")
                val arrowEnd = source.indexOf("->") + 2

                val verdict = ExplicitItLambdaParameterDecision
                    .decide(vpListStart = vpListStart, lbraceEnd = lbraceEnd, arrowEnd = arrowEnd, hasType = false, hasComment = true)

                verdict.message shouldBe ExplicitItLambdaParameterDecision.UNTYPED_MESSAGE
                verdict.edits shouldBe emptyList()
            }
        },
    )
