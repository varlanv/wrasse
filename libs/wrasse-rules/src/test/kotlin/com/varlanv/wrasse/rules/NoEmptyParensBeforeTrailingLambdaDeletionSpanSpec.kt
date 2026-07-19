package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NoEmptyParensBeforeTrailingLambdaDeletionSpanSpec : BaseSpec({

    should("delete the empty parentheses, leaving the space before the brace untouched") {
        val source = "list.map() { it }"
        val parensStart = source.indexOf("()")
        val parensEnd = parensStart + 2

        val edit = NoEmptyParensBeforeTrailingLambdaDeletionSpan.compute(parensStart, parensEnd)

        edit.startOffset shouldBe parensStart
        edit.endOffset shouldBe parensEnd
        edit.replacement shouldBe ""
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "list.map { it }"
    }

    should("collapse parentheses directly adjacent to the callee with no space before the lambda") {
        val source = "foo(){ it }"
        val parensStart = source.indexOf("()")
        val parensEnd = parensStart + 2

        val edit = NoEmptyParensBeforeTrailingLambdaDeletionSpan.compute(parensStart, parensEnd)

        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "foo{ it }"
    }

    should("leave an extra space before the parentheses untouched") {
        val source = "foo ()  { it }"
        val parensStart = source.indexOf("()")
        val parensEnd = parensStart + 2

        val edit = NoEmptyParensBeforeTrailingLambdaDeletionSpan.compute(parensStart, parensEnd)

        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "foo   { it }"
    }

    should("leave a same-line block comment between the parentheses and the brace untouched") {
        val source = "foo() /* c */ { it }"
        val parensStart = source.indexOf("()")
        val parensEnd = parensStart + 2

        val edit = NoEmptyParensBeforeTrailingLambdaDeletionSpan.compute(parensStart, parensEnd)

        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "foo /* c */ { it }"
    }
})
