package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EmptyDefaultConstructorDecisionSpec : BaseSpec({

    fun decide(
        hasValueParameter: Boolean = false,
        hasAnnotation: Boolean = false,
        visibility: WNodeType? = null,
        isExpectOrActual: Boolean = false,
        calledWithEmptyThis: Boolean = false,
        hasKeyword: Boolean = false,
        hasCommentInParens: Boolean = false,
        vpStart: Int = 10,
        vpEnd: Int = 12,
    ) = EmptyDefaultConstructorDecision.decide(
        hasValueParameter = hasValueParameter,
        hasAnnotation = hasAnnotation,
        visibility = visibility,
        isExpectOrActual = isExpectOrActual,
        calledWithEmptyThis = calledWithEmptyThis,
        hasKeyword = hasKeyword,
        hasCommentInParens = hasCommentInParens,
        vpStart = vpStart,
        vpEnd = vpEnd,
    )

    should("report and fix a plain empty constructor with no modifiers or keyword") {
        val verdict = decide()

        verdict shouldNotBe null
        verdict!!.edits.size shouldBe 1
        val edit = verdict.edits.single()
        edit.startOffset shouldBe 10
        edit.endOffset shouldBe 12
        edit.replacement shouldBe ""
    }

    should("splice the () away from a real source string") {
        val source = "class Foo()"
        val vpStart = source.indexOf("(")
        val vpEnd = source.indexOf(")") + 1

        val verdict = decide(vpStart = vpStart, vpEnd = vpEnd)!!
        val edit = verdict.edits.single()
        val fixed = source.substring(0, edit.startOffset) + edit.replacement + source.substring(edit.endOffset)

        fixed shouldBe "class Foo"
    }

    should("report but bail the fix when the constructor keyword is present") {
        val verdict = decide(hasKeyword = true)

        verdict shouldNotBe null
        verdict!!.edits shouldBe emptyList()
    }

    should("report but bail the fix when a comment sits inside the parens") {
        val verdict = decide(hasCommentInParens = true)

        verdict shouldNotBe null
        verdict!!.edits shouldBe emptyList()
    }

    should("not report at all when the constructor has a value parameter") {
        decide(hasValueParameter = true) shouldBe null
    }

    should("not report at all when the constructor carries an annotation") {
        decide(hasAnnotation = true) shouldBe null
    }

    should("report a constructor with an explicit public visibility modifier") {
        decide(visibility = WNodeType.KW_PUBLIC) shouldNotBe null
    }

    should("not report at all for a private, protected, or internal constructor") {
        decide(visibility = WNodeType.KW_PRIVATE) shouldBe null
        decide(visibility = WNodeType.KW_PROTECTED) shouldBe null
        decide(visibility = WNodeType.KW_INTERNAL) shouldBe null
    }

    should("not report at all for an expect or actual class") {
        decide(isExpectOrActual = true) shouldBe null
    }

    should("not report at all when a sibling secondary constructor delegates via zero-arg this()") {
        decide(calledWithEmptyThis = true) shouldBe null
    }
})
