package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NotImplementedDeclarationDecisionSpec : BaseSpec({

    should("report throwing NotImplementedError") {
        NotImplementedDeclarationDecision.decideThrow("NotImplementedError") shouldBe
            NotImplementedDeclarationDecision.MESSAGE
    }

    should("not report throwing an unrelated exception") {
        NotImplementedDeclarationDecision.decideThrow("IllegalStateException") shouldBe null
    }

    should("report a zero-argument TODO call") {
        NotImplementedDeclarationDecision.decideTodoCall("TODO", 0) shouldBe NotImplementedDeclarationDecision.MESSAGE
    }

    should("report a one-argument TODO call") {
        NotImplementedDeclarationDecision.decideTodoCall("TODO", 1) shouldBe NotImplementedDeclarationDecision.MESSAGE
    }

    should("not report a TODO call with more than one argument") {
        NotImplementedDeclarationDecision.decideTodoCall("TODO", 2) shouldBe null
    }

    should("not report an unrelated call") {
        NotImplementedDeclarationDecision.decideTodoCall("println", 0) shouldBe null
    }
})
