package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MissingPackageDeclarationDecisionSpec :
    BaseSpec(
        {

            should("report a file with no package name") {
                MissingPackageDeclarationDecision.decide(hasPackageName = false) shouldBe MissingPackageDeclarationDecision.MESSAGE
            }

            should("not report a file with a package name") {
                MissingPackageDeclarationDecision.decide(hasPackageName = true) shouldBe null
            }
        },
    )
