package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class UndocumentedPublicApiDecisionSpec : BaseSpec({

    should("report an undocumented public class") {
        UndocumentedPublicApiDecision.decideClass("Foo", hasKdoc = false, isPublic = true) shouldBe
            "Foo is missing required documentation."
    }

    should("not report a documented public class") {
        UndocumentedPublicApiDecision.decideClass("Foo", hasKdoc = true, isPublic = true) shouldBe null
    }

    should("not report a non-public class") {
        UndocumentedPublicApiDecision.decideClass("Foo", hasKdoc = false, isPublic = false) shouldBe null
    }

    should("report an undocumented public function") {
        UndocumentedPublicApiDecision.decideFunction(
            "foo",
            hasKdoc = false,
            isPublic = true,
            isOverride = false,
        ) shouldBe "The function foo is missing documentation."
    }

    should("not report an undocumented public override function") {
        UndocumentedPublicApiDecision.decideFunction(
            "foo",
            hasKdoc = false,
            isPublic = true,
            isOverride = true,
        ) shouldBe null
    }

    should("report an undocumented public property") {
        UndocumentedPublicApiDecision.decideProperty(
            "foo",
            hasKdoc = false,
            isPublic = true,
            isOverride = false,
        ) shouldBe "The property foo is missing documentation."
    }

    should("not report an undocumented public override property") {
        UndocumentedPublicApiDecision.decideProperty(
            "foo",
            hasKdoc = false,
            isPublic = true,
            isOverride = true,
        ) shouldBe null
    }
})
