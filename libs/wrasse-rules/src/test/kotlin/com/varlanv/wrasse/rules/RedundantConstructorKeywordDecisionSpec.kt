package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class RedundantConstructorKeywordDecisionSpec : BaseSpec({

    should("delete from the deletion start through the keyword's own end") {
        val source = "class Foo constructor(x: Int)"
        val deletionStart = source.indexOf(" constructor")
        val keywordEnd = source.indexOf("(")

        val edits = RedundantConstructorKeywordDecision.decide(deletionStart, keywordEnd)

        edits.size shouldBe 1
        val edit = edits.single()
        edit.startOffset shouldBe deletionStart
        edit.endOffset shouldBe keywordEnd
        edit.replacement shouldBe ""

        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)
        fixed shouldBe "class Foo(x: Int)"
    }

    should("collapse a multiline gap between the class name and the keyword") {
        val source = "class Foo\nconstructor(x: Int)"
        val deletionStart = source.indexOf("\n")
        val keywordEnd = source.indexOf("(")

        val edits = RedundantConstructorKeywordDecision.decide(deletionStart, keywordEnd)

        val edit = edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)
        fixed shouldBe "class Foo(x: Int)"
    }
})
