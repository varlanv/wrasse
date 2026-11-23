package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.MatrixDirective
import com.varlanv.wrasse.model.MatrixDirectives
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MatrixMessagesSpec : BaseSpec({

    fun directive(sizeText: String): MatrixDirective {
        val text = "// wrasse matrix:$sizeText"
        val directives = MatrixDirectives()
        directives.add(text, 0, text.length, 0, "$text\nval x = f(1)\n")
        return directives.all().single()
    }

    should("name the text and the minimum for a size that is not a whole number") {
        MatrixMessages.sizeProblem(directive("abc")) shouldBe
            "invalid size 'abc' in '// wrasse matrix:abc': the size must be a whole number of at least 2"
        MatrixMessages.sizeProblem(directive("")) shouldBe
            "invalid size '' in '// wrasse matrix:': the size must be a whole number of at least 2"
        MatrixMessages.sizeProblem(directive("2 ")) shouldBe
            "invalid size '2 ' in '// wrasse matrix:2 ': the size must be a whole number of at least 2"
    }

    should("name the size and the minimum for a size below two") {
        MatrixMessages.sizeProblem(directive("-1")) shouldBe
            "size -1 in '// wrasse matrix:-1' is below the minimum of 2: a row needs at least 2 elements"
        MatrixMessages.sizeProblem(directive("1")) shouldBe
            "size 1 in '// wrasse matrix:1' is below the minimum of 2: a row needs at least 2 elements"
    }

    should("report no size problem for a size of two or more") {
        MatrixMessages.sizeProblem(directive("2")) shouldBe null
        MatrixMessages.sizeProblem(directive("1000")) shouldBe null
    }

    should("word every call problem with the directive text") {
        val target = directive("3")

        MatrixMessages.noCall(target) shouldBe
            "'// wrasse matrix:3' has no call to lay out: no call with a parenthesized argument list starts on " +
                "the line directly below it (a function, constructor or class declaration is not a call)"
        MatrixMessages.unresolvedCallee(target) shouldBe
            "'// wrasse matrix:3' targets a call whose callee the compiler could not resolve"
        MatrixMessages.notSingleVarargCallee(target, "table") shouldBe
            "'// wrasse matrix:3' targets a call to 'table', which does not declare exactly one parameter that " +
                "is a vararg"
        MatrixMessages.trailingLambda(target) shouldBe
            "'// wrasse matrix:3' does not support a call with a trailing lambda"
        MatrixMessages.comment(target) shouldBe
            "'// wrasse matrix:3' does not support a comment inside the argument list of the call below"
        MatrixMessages.spreadArgument(target) shouldBe
            "'// wrasse matrix:3' does not support a spread argument in the call below"
        MatrixMessages.namedArgument(target) shouldBe
            "'// wrasse matrix:3' does not support a named argument in the call below"
        MatrixMessages.multilineElement(target) shouldBe
            "'// wrasse matrix:3' needs every element on one line, but an element of the call below must stay " +
                "on several lines"
    }

    should("word the count problems with the size and the count") {
        val target = directive("4")

        MatrixMessages.sizeExceedsCount(target, 4, 3) shouldBe
            "'// wrasse matrix:4' asks for rows of 4 elements but the call below has only 3"
        MatrixMessages.countNotDivisible(target, 4, 6) shouldBe
            "'// wrasse matrix:4' cannot split the 6 elements of the call below into rows of 4: 6 is not divisible " +
                "by 4"
    }

    should("put a size problem before a recorded failure and a missing call after it") {
        val target = directive("abc")
        target.fail("recorded")

        MatrixMessages.problemOf(target) shouldBe MatrixMessages.sizeProblem(target)
    }

    should("report a recorded failure of a claimed directive") {
        val directives = MatrixDirectives()
        directives.add("// wrasse matrix:2", 0, 18, 0, "// wrasse matrix:2\nval x = f(1)\n")
        val target = directives.all().single()
        directives.claim(20)
        target.fail("recorded")

        MatrixMessages.problemOf(target) shouldBe "recorded"
    }

    should("report no problem for a claimed directive without a failure") {
        val directives = MatrixDirectives()
        directives.add("// wrasse matrix:2", 0, 18, 0, "// wrasse matrix:2\nval x = f(1)\n")
        directives.claim(20)

        MatrixMessages.problemOf(directives.all().single()) shouldBe null
    }

    should("report a directive nothing claimed as having no call") {
        val target = directive("2")

        MatrixMessages.problemOf(target) shouldBe MatrixMessages.noCall(target)
    }
})
