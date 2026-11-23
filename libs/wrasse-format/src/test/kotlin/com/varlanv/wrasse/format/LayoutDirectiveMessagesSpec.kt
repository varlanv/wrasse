package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.AnnotationSpans
import com.varlanv.wrasse.model.FormatRegions
import com.varlanv.wrasse.model.LayoutDirective
import com.varlanv.wrasse.model.LayoutDirectiveKind
import com.varlanv.wrasse.model.LayoutDirectives
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LayoutDirectiveMessagesSpec : BaseSpec({

    fun directivesOf(source: String): LayoutDirectives {
        val directives = LayoutDirectives(AnnotationSpans())
        var lineStart = 0
        while (lineStart < source.length) {
            val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
            val text = source.substring(lineStart, lineEnd)
            LayoutDirectiveKind.ofText(text)?.let { directives.add(it, text, lineStart, lineEnd, lineStart, source) }
            lineStart = lineEnd + 1
        }
        return directives
    }

    fun directive(
        sizeText: String,
        kind: LayoutDirectiveKind = LayoutDirectiveKind.MATRIX,
    ): LayoutDirective {
        val text = "${kind.prefix}$sizeText"
        return directivesOf("$text\nval x = f(1)\n").all().single()
    }

    should("name the text and the minimum for a size that is not a whole number") {
        LayoutDirectiveMessages.sizeProblem(directive("abc")) shouldBe
            "invalid size 'abc' in '// wrasse matrix:abc': the size must be a whole number of at least 2"
        LayoutDirectiveMessages.sizeProblem(directive("")) shouldBe
            "invalid size '' in '// wrasse matrix:': the size must be a whole number of at least 2"
        LayoutDirectiveMessages.sizeProblem(directive("2 ")) shouldBe
            "invalid size '2 ' in '// wrasse matrix:2 ': the size must be a whole number of at least 2"
    }

    should("name the columns directive in the size problems of one") {
        val kind = LayoutDirectiveKind.COLUMNS

        LayoutDirectiveMessages.sizeProblem(directive("abc", kind)) shouldBe
            "invalid size 'abc' in '// wrasse columns:abc': the size must be a whole number of at least 2"
        LayoutDirectiveMessages.sizeProblem(directive("1", kind)) shouldBe
            "size 1 in '// wrasse columns:1' is below the minimum of 2: a row needs at least 2 elements"
    }

    should("name the size and the minimum for a size below two") {
        LayoutDirectiveMessages.sizeProblem(directive("-1")) shouldBe
            "size -1 in '// wrasse matrix:-1' is below the minimum of 2: a row needs at least 2 elements"
        LayoutDirectiveMessages.sizeProblem(directive("1")) shouldBe
            "size 1 in '// wrasse matrix:1' is below the minimum of 2: a row needs at least 2 elements"
    }

    should("report no size problem for a size of two or more") {
        LayoutDirectiveMessages.sizeProblem(directive("2")) shouldBe null
        LayoutDirectiveMessages.sizeProblem(directive("1000")) shouldBe null
        LayoutDirectiveMessages.sizeProblem(directive("2", LayoutDirectiveKind.COLUMNS)) shouldBe null
    }

    should("word every call problem with the directive text") {
        val target = directive("3")

        LayoutDirectiveMessages.noCall(target) shouldBe
            "'// wrasse matrix:3' has no call to lay out: no call with a parenthesized argument list starts on " +
                "the first line below it that is neither an annotation-only line nor another layout directive " +
                "(a function, constructor or class declaration is not a call)"
        LayoutDirectiveMessages.unresolvedCallee(target) shouldBe
            "'// wrasse matrix:3' targets a call whose callee the compiler could not resolve"
        LayoutDirectiveMessages.notSingleVarargCallee(target, "table") shouldBe
            "'// wrasse matrix:3' targets a call to 'table', which does not declare exactly one parameter that " +
                "is a vararg"
        LayoutDirectiveMessages.trailingLambda(target) shouldBe
            "'// wrasse matrix:3' does not support a call with a trailing lambda"
        LayoutDirectiveMessages.comment(target) shouldBe
            "'// wrasse matrix:3' does not support a comment inside the argument list of the call below"
        LayoutDirectiveMessages.spreadArgument(target) shouldBe
            "'// wrasse matrix:3' does not support a spread argument in the call below"
        LayoutDirectiveMessages.namedArgument(target) shouldBe
            "'// wrasse matrix:3' does not support a named argument in the call below"
        LayoutDirectiveMessages.multilineElement(target) shouldBe
            "'// wrasse matrix:3' needs every element on one line, but an element of the call below must stay " +
                "on several lines"
    }

    should("name the columns directive in every call problem of one") {
        val target = directive("3", LayoutDirectiveKind.COLUMNS)

        LayoutDirectiveMessages.unresolvedCallee(target) shouldBe
            "'// wrasse columns:3' targets a call whose callee the compiler could not resolve"
        LayoutDirectiveMessages.multilineElement(target) shouldBe
            "'// wrasse columns:3' needs every element on one line, but an element of the call below must stay " +
                "on several lines"
    }

    should("word the count problems with the size and the count") {
        val target = directive("4")

        LayoutDirectiveMessages.sizeExceedsCount(target, 4, 3) shouldBe
            "'// wrasse matrix:4' asks for rows of 4 elements but the call below has only 3"
        LayoutDirectiveMessages.countNotDivisible(target, 4, 6) shouldBe
            "'// wrasse matrix:4' cannot split the 6 elements of the call below into rows of 4: 6 is not divisible " +
                "by 4"
    }

    should("name both directives of a clash with their lines") {
        val source = "\n\n\n// wrasse matrix:3\n// wrasse columns:3\nval x = f(1)\n"
        val clashing = directivesOf(source).all()

        LayoutDirectiveMessages.clash(clashing) shouldBe
            "'// wrasse matrix:3' (line 4) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
    }

    should("name every directive of a clash of three") {
        val source = "// wrasse columns:2\n@A\n// wrasse matrix:4\n// wrasse columns:5\nval x = f(1)\n"
        val clashing = directivesOf(source).all()

        LayoutDirectiveMessages.clash(clashing) shouldBe
            "'// wrasse columns:2' (line 1), '// wrasse matrix:4' (line 3) and '// wrasse columns:5' (line 4) all " +
                "target the call below; keep only one"
    }

    should("put a size problem before a recorded failure and a missing call after it") {
        val target = directive("abc")
        target.fail("recorded")

        LayoutDirectiveMessages.problemOf(target) shouldBe LayoutDirectiveMessages.sizeProblem(target)
    }

    should("report a recorded failure of a claimed directive") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"
        val directives = directivesOf(source)
        val target = directives.all().single()
        directives.claim(source.indexOf("(1)"), source, FormatRegions())
        target.fail("recorded")

        LayoutDirectiveMessages.problemOf(target) shouldBe "recorded"
    }

    should("report no problem for a claimed directive without a failure") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"
        val directives = directivesOf(source)
        directives.claim(source.indexOf("(1)"), source, FormatRegions())

        LayoutDirectiveMessages.problemOf(directives.all().single()) shouldBe null
    }

    should("report a directive nothing claimed as having no call") {
        val target = directive("2")

        LayoutDirectiveMessages.problemOf(target) shouldBe LayoutDirectiveMessages.noCall(target)
    }
})
