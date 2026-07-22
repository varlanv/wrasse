package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class CommentOverPrivateDeclarationDecisionSpec :
    BaseSpec(
        {

            should("report a documented private function") {
                CommentOverPrivateDeclarationDecision.decideFunction(hasKdoc = true, isPrivate = true, name = "helper") shouldBe
                    "The function helper has a comment. Prefer renaming the function giving it a more self-explanatory name."
            }

            should("not report an undocumented private function") {
                CommentOverPrivateDeclarationDecision.decideFunction(hasKdoc = false, isPrivate = true, name = "helper") shouldBe null
            }

            should("not report a documented public function") {
                CommentOverPrivateDeclarationDecision.decideFunction(hasKdoc = true, isPrivate = false, name = "helper") shouldBe null
            }

            should("report a documented private property") {
                CommentOverPrivateDeclarationDecision.decideProperty(hasKdoc = true, isPrivate = true) shouldBe
                    "Private properties should be named in a self-explanatory manner without the need for a comment."
            }

            should("not report a documented public property") {
                CommentOverPrivateDeclarationDecision.decideProperty(hasKdoc = true, isPrivate = false) shouldBe null
            }
        },
    )
