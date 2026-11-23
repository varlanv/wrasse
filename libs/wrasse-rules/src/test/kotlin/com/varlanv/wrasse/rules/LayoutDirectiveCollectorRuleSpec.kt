package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.LayoutDirectiveKind
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class LayoutDirectiveCollectorRuleSpec : BaseSpec({

    val reporter = object : WReporter {
        override val reports: List<ViolationReport> = emptyList()

        override fun report(
            ruleId: String,
            message: String,
            startOffset: Int,
            endOffset: Int,
            rule: WRule,
            edits: List<WEdit>,
        ) = Unit
    }

    data class Collected(
        val kind: LayoutDirectiveKind,
        val text: String,
        val commentStart: Int,
        val lineStart: Int,
    )

    fun matrix(
        text: String,
        commentStart: Int,
        lineStart: Int,
    ) = Collected(LayoutDirectiveKind.MATRIX, text, commentStart, lineStart)

    fun columns(
        text: String,
        commentStart: Int,
        lineStart: Int,
    ) = Collected(LayoutDirectiveKind.COLUMNS, text, commentStart, lineStart)

    fun collect(source: String): List<Collected> {
        val ctx = WContext(filePath = "Sample.kt")
        ctx.sourceText = source
        val rule = LayoutDirectiveCollectorRule()
        var from = source.indexOf("//")
        while (from >= 0) {
            val lineEnd = source.indexOf('\n', from).let { if (it < 0) source.length else it }
            ctx.type = WNodeType.EOL_COMMENT
            ctx.startOffset = from
            ctx.endOffset = lineEnd
            ctx.enterLeaf()
            rule.visitLeaf(ctx, reporter)
            from = source.indexOf("//", lineEnd)
        }
        return ctx.layoutDirectives.all().map { Collected(it.kind, it.text, it.commentStart, it.lineStart) }
    }

    should("record a directive alone on its line") {
        collect("a\n// wrasse matrix:2\nval x = f(1)\n") shouldBe listOf(matrix("// wrasse matrix:2", 2, 2))
    }

    should("record an indented directive with the start of its line") {
        collect("a\n    // wrasse matrix:3\nval x = f(1)\n") shouldBe listOf(matrix("// wrasse matrix:3", 6, 2))
    }

    should("accept tab indentation before a directive") {
        collect("a\n\t \t// wrasse matrix:3\nval x = f(1)\n") shouldBe listOf(matrix("// wrasse matrix:3", 5, 2))
    }

    should("record a directive at the very start of the file") {
        collect("// wrasse matrix:2\nval x = f(1)\n") shouldBe listOf(matrix("// wrasse matrix:2", 0, 0))
    }

    should("record a directive whose size is not a number so the printer can reject it") {
        collect("a\n// wrasse matrix:abc\nb\n") shouldBe listOf(matrix("// wrasse matrix:abc", 2, 2))
        collect("a\n// wrasse matrix:\nb\n") shouldBe listOf(matrix("// wrasse matrix:", 2, 2))
        collect("a\n// wrasse matrix:2 \nb\n") shouldBe listOf(matrix("// wrasse matrix:2 ", 2, 2))
    }

    should("record several directives in source order") {
        collect("// wrasse matrix:2\na\n// wrasse matrix:5\nb\n").map { it.text } shouldBe
            listOf("// wrasse matrix:2", "// wrasse matrix:5")
    }

    should("record a columns directive with the kind it was written with") {
        collect("a\n    // wrasse columns:3\nval x = f(1)\n") shouldBe listOf(columns("// wrasse columns:3", 6, 2))
    }

    should("record directives of both kinds in source order") {
        collect("// wrasse matrix:2\n// wrasse columns:3\nval x = f(1)\n").map { it.kind to it.text } shouldBe
            listOf(
                LayoutDirectiveKind.MATRIX to "// wrasse matrix:2",
                LayoutDirectiveKind.COLUMNS to "// wrasse columns:3",
            )
    }

    should("record a columns directive whose size is not a number so the printer can reject it") {
        collect("a\n// wrasse columns:abc\nb\n") shouldBe listOf(columns("// wrasse columns:abc", 2, 2))
        collect("a\n// wrasse columns:\nb\n") shouldBe listOf(columns("// wrasse columns:", 2, 2))
    }

    should("ignore a columns directive that shares its line with code") {
        collect("val a = f(1) // wrasse columns:2\nval b = 2\n") shouldBe emptyList()
        collect("/* x */ // wrasse columns:2\nval b = f(1)\n") shouldBe emptyList()
    }

    should("ignore a directive that shares its line with code") {
        collect("val a = f(1) // wrasse matrix:2\nval b = 2\n") shouldBe emptyList()
    }

    should("ignore a directive that follows a block comment on its line") {
        collect("/* x */ // wrasse matrix:2\nval b = f(1)\n") shouldBe emptyList()
    }

    should("ignore a directive with a carriage return in front of the comment") {
        collect("a\n\r// wrasse matrix:2\nval b = f(1)\n") shouldBe emptyList()
    }

    should("ignore every near-miss directive text") {
        val nearMisses = listOf(
            "//wrasse matrix:2",
            "// wrasse  matrix:2",
            "// Wrasse matrix:2",
            "// wrasse matrix",
            "// wrasse matrix :2",
            "//\twrasse matrix:2",
            "// wrasse\tmatrix:2",
            "// wrasse fmt:off",
            "//wrasse columns:2",
            "// wrasse  columns:2",
            "// Wrasse columns:2",
            "// wrasse columns",
            "// wrasse columns :2",
            "//\twrasse columns:2",
            "// wrasse\tcolumns:2",
        )
        for (nearMiss in nearMisses) {
            collect("a\n$nearMiss\nval b = f(1)\n") shouldBe emptyList()
        }
    }
})
