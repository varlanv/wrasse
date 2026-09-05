package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MixedArgumentsDecisionSpec : BaseSpec({

    fun named(text: String) = MixedArgumentsDecision.isNamedArgument(text, 0, text.length)

    should("recognize plain and backticked named arguments") {
        named("x = 1") shouldBe true
        named("x=1") shouldBe true
        named("  `weird name` = f()") shouldBe true
    }

    should("not treat comparisons, positional values, or lambdas as named") {
        named("x == 1") shouldBe false
        named("1") shouldBe false
        named("foo(a = 1)") shouldBe false
        named("{ a -> a }") shouldBe false
        named("`unterminated") shouldBe false
    }

    should("flag only lists holding both kinds") {
        MixedArgumentsDecision.mixesNamedAndPositional(named = 1, positional = 1) shouldBe true
        MixedArgumentsDecision.mixesNamedAndPositional(named = 2, positional = 0) shouldBe false
        MixedArgumentsDecision.mixesNamedAndPositional(named = 0, positional = 3) shouldBe false
    }
})
