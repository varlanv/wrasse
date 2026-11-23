package com.varlanv.wrasse.model

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class MatrixDirectiveSpec : BaseSpec({

    fun directive(
        text: String,
        source: String = "$text\nval x = f(1)\n",
    ): MatrixDirective {
        val directives = MatrixDirectives()
        directives.add(text, 0, text.length, 0, source)
        return directives.all().single()
    }

    should("recognise a comment starting with exactly the prefix") {
        MatrixDirective.isDirectiveText("// wrasse matrix:2") shouldBe true
        MatrixDirective.isDirectiveText("// wrasse matrix:") shouldBe true
        MatrixDirective.isDirectiveText("// wrasse matrix:abc") shouldBe true
    }

    should("reject every near-miss prefix") {
        val nearMisses = listOf(
            "//wrasse matrix:2",
            "// wrasse  matrix:2",
            "// Wrasse matrix:2",
            "// wrasse Matrix:2",
            "// wrasse matrix :2",
            "// wrasse matrix",
            " // wrasse matrix:2",
            "/* wrasse matrix:2 */",
            "// wrasse\tmatrix:2",
            "",
        )
        nearMisses.map { MatrixDirective.isDirectiveText(it) } shouldBe nearMisses.map { false }
    }

    should("read the size of a whole number") {
        directive("// wrasse matrix:2").size shouldBe 2
        directive("// wrasse matrix:16").size shouldBe 16
        directive("// wrasse matrix:007").size shouldBe 7
        directive("// wrasse matrix:2147483647").size shouldBe Int.MAX_VALUE
    }

    should("read a negative size so its value can be reported") {
        directive("// wrasse matrix:-1").size shouldBe -1
        directive("// wrasse matrix:-0").size shouldBe 0
        directive("// wrasse matrix:-2147483648").size shouldBe Int.MIN_VALUE
    }

    should("read no size from anything but an optional minus and ascii digits") {
        val invalid = listOf("", "abc", "2x", "x2", "2 ", " 2", "+2", "--2", "-", "2.0", "1_0", "٢", "2147483648")
        invalid.map { directive("// wrasse matrix:$it").size } shouldBe invalid.map { null }
    }

    should("keep the text after the prefix as the size text") {
        directive("// wrasse matrix:2 ").sizeText shouldBe "2 "
        directive("// wrasse matrix:").sizeText shouldBe ""
    }

    should("target the line directly below the comment") {
        val source = "// wrasse matrix:2\nval x = f(1)\nval y = 2\n"
        val target = directive("// wrasse matrix:2", source)

        target.targetLineStart shouldBe 19
        target.targetLineEnd shouldBe 31
        target.targetsLineOf(19) shouldBe true
        target.targetsLineOf(30) shouldBe true
        target.targetsLineOf(31) shouldBe false
        target.targetsLineOf(18) shouldBe false
    }

    should("target the last line of a file without a trailing newline") {
        val target = directive("// wrasse matrix:2", "// wrasse matrix:2\nval x = f(1)")

        target.targetLineStart shouldBe 19
        target.targetLineEnd shouldBe 31
    }

    should("target no line when the comment ends the file") {
        val atEnd = directive("// wrasse matrix:2", "// wrasse matrix:2")
        val beforeEnd = directive("// wrasse matrix:2", "// wrasse matrix:2\n")

        atEnd.targetsLineOf(0) shouldBe false
        atEnd.targetsLineOf(18) shouldBe false
        beforeEnd.targetsLineOf(18) shouldBe false
        beforeEnd.targetsLineOf(19) shouldBe false
    }

    should("target an empty line below without holding any offset") {
        val target = directive("// wrasse matrix:2", "// wrasse matrix:2\n\nval x = f(1)\n")

        target.targetLineStart shouldBe 19
        target.targetLineEnd shouldBe 19
        target.targetsLineOf(19) shouldBe false
    }

    should("claim the first unclaimed directive whose line below holds the offset") {
        val source = "// wrasse matrix:2\nval x = f(1)\n// wrasse matrix:3\nval y = g(2)\n"
        val directives = MatrixDirectives()
        directives.add("// wrasse matrix:2", 0, 18, 0, source)
        directives.add("// wrasse matrix:3", 32, 50, 32, source)
        val (first, second) = directives.all()

        directives.claim(40) shouldBe null
        directives.claim(60) shouldBe second
        directives.claim(60) shouldBe null
        directives.claim(25) shouldBe first
        directives.claim(25) shouldBe null
        first.claimed shouldBe true
        second.claimed shouldBe true
    }

    should("keep only the first failure recorded") {
        val target = directive("// wrasse matrix:2")
        target.failure shouldBe null

        target.fail("first")
        target.fail("second")

        target.failure shouldBe "first"
    }

    should("lie inside a region holding its own line or the line below") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"
        val target = directive("// wrasse matrix:2", source)
        val own = FormatRegions().apply { addSpan(0, 5) }
        val below = FormatRegions().apply { addSpan(19, 25) }
        val elsewhere = FormatRegions().apply { addSpan(40, 50) }

        target.isInside(own) shouldBe true
        target.isInside(below) shouldBe true
        target.isInside(elsewhere) shouldBe false
        target.isInside(FormatRegions()) shouldBe false
    }

    should("report no directives as empty") {
        MatrixDirectives().isEmpty shouldBe true
        MatrixDirectives().claim(0) shouldBe null
    }
})
