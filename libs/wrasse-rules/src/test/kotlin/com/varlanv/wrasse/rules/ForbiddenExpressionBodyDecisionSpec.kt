package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.IndentScope
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ForbiddenExpressionBodyDecisionSpec : BaseSpec({

    fun render(edits: List<WEdit>) = edits.map { Triple(it.startOffset, it.endOffset, it.replacement) }

    should("bake indentation and a return prefix with format off") {
        val edits = ForbiddenExpressionBodyDecision.edits(
            gapStart = 20,
            bodyStart = 23,
            bodyEnd = 30,
            returnsUnit = false,
            bodyIsThrow = false,
            formatEnabled = false,
            baseIndentColumn = 4,
            indentWidth = 4,
        )
        render(edits) shouldBe listOf(Triple(20, 23, " {\n        return "), Triple(30, 30, "\n    }"))
        edits.all { it.indentScope == IndentScope.NONE } shouldBe true
    }

    should("omit the return prefix for a declared Unit type or a throw body") {
        ForbiddenExpressionBodyDecision.edits(
            0,
            3,
            9,
            returnsUnit = true,
            bodyIsThrow = false,
            formatEnabled = false,
            baseIndentColumn = 0,
            indentWidth = 4,
        )[0].replacement shouldBe " {\n    "
        ForbiddenExpressionBodyDecision.edits(
            0,
            3,
            9,
            returnsUnit = false,
            bodyIsThrow = true,
            formatEnabled = false,
            baseIndentColumn = 0,
            indentWidth = 4,
        )[0].replacement shouldBe " {\n    "
    }

    should("emit indent-scoped, unindented edits with format on") {
        val edits = ForbiddenExpressionBodyDecision.edits(
            20,
            23,
            30,
            returnsUnit = false,
            bodyIsThrow = false,
            formatEnabled = true,
            baseIndentColumn = 4,
            indentWidth = 4,
        )
        render(edits) shouldBe listOf(Triple(20, 23, " {\nreturn "), Triple(30, 30, "\n}"))
        edits.map { it.indentScope } shouldBe listOf(IndentScope.OPEN, IndentScope.CLOSE)
    }

    should("recognise a Unit return type written plainly or qualified") {
        ForbiddenExpressionBodyDecision.isUnitTypeText("Unit") shouldBe true
        ForbiddenExpressionBodyDecision.isUnitTypeText("kotlin.Unit") shouldBe true
        ForbiddenExpressionBodyDecision.isUnitTypeText("Int") shouldBe false
    }
})
