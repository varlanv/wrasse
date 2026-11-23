package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.LayoutDirectiveKind
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FormatLayoutDirectiveRuleSpec : BaseSpec({

    data class Reported(
        val ruleId: String,
        val message: String,
        val start: Int,
        val end: Int,
        val level: RuleLevel,
        val edits: List<WEdit>,
    )

    fun contextOf(source: String): WContext {
        val ctx = WContext(filePath = "Sample.kt")
        ctx.sourceText = source
        var lineStart = 0
        while (lineStart < source.length) {
            val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
            val text = source.substring(lineStart, lineEnd)
            LayoutDirectiveKind
                .ofText(text)
                ?.let { ctx.layoutDirectives.add(it, text, lineStart, lineEnd, lineStart, source) }
            lineStart = lineEnd + 1
        }
        return ctx
    }

    fun visit(
        source: String,
        kind: LayoutDirectiveKind = LayoutDirectiveKind.MATRIX,
        level: RuleLevel = RuleLevel.ERROR,
        prepare: (WContext) -> Unit = {},
    ): List<Reported> {
        val ctx = contextOf(source)
        prepare(ctx)
        val reported = mutableListOf<Reported>()
        val config = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = level)
        val rule = FormatLayoutDirectiveRule(kind, config)
        val reporter = object : WReporter {
            override val reports: List<ViolationReport> = emptyList()

            override fun report(
                ruleId: String,
                message: String,
                startOffset: Int,
                endOffset: Int,
                rule: WRule,
                edits: List<WEdit>,
            ) {
                reported.add(Reported(ruleId, message, startOffset, endOffset, rule.config.effectiveLevel, edits))
            }
        }
        rule.visit(ctx, reporter)
        return reported
    }

    fun claimCall(
        ctx: WContext,
        call: String,
    ) = ctx.layoutDirectives.claim(ctx.sourceText.indexOf(call) + call.indexOf('('), ctx.sourceText, ctx.formatRegions)

    val noCallMessage = "'// wrasse matrix:2' has no call to lay out: no call with a parenthesized argument list " +
        "starts on the first line below it that is neither an annotation-only line nor another layout directive " +
        "(a function, constructor or class declaration is not a call)"

    should("name each rule by the rule id of its kind") {
        val config = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = RuleLevel.ERROR)

        FormatLayoutDirectiveRule(LayoutDirectiveKind.MATRIX, config).id shouldBe "format-matrix"
        FormatLayoutDirectiveRule(LayoutDirectiveKind.COLUMNS, config).id shouldBe "format-columns"
    }

    should("report a directive no call claimed at the directive comment under the rule id format-matrix") {
        val source = "val a = 1\n// wrasse matrix:2\nfun f() = 1\n"

        visit(source) shouldBe listOf(Reported("format-matrix", noCallMessage, 10, 28, RuleLevel.ERROR, emptyList()))
    }

    should("report an unclaimed columns directive under the rule id format-columns") {
        val source = "val a = 1\n// wrasse columns:2\nfun f() = 1\n"

        visit(source, LayoutDirectiveKind.COLUMNS) shouldBe
            listOf(
                Reported(
                    "format-columns",
                    noCallMessage.replace("matrix", "columns"),
                    10,
                    29,
                    RuleLevel.ERROR,
                    emptyList(),
                ),
            )
    }

    should("report only the directives of its own kind") {
        val source = "// wrasse matrix:2\n// wrasse columns:3\nfun f() = 1\n"

        visit(source, LayoutDirectiveKind.MATRIX).map { it.start } shouldBe listOf(0)
        visit(source, LayoutDirectiveKind.COLUMNS).map { it.start } shouldBe listOf(19)
    }

    should("report with the level of the format setting") {
        visit("// wrasse matrix:2\nfun f() = 1\n", level = RuleLevel.WARN).map { it.level } shouldBe
            listOf(RuleLevel.WARN)
    }

    should("report a size problem even for a claimed directive") {
        val source = "// wrasse matrix:abc\nval x = f(1)\n"

        visit(source) { claimCall(it, "f(1)") }.map { it.message } shouldBe
            listOf("invalid size 'abc' in '// wrasse matrix:abc': the size must be a whole number of at least 2")
    }

    should("report a size problem with the minimum of the kind of the directive") {
        val source = "// wrasse columns:0\n// wrasse matrix:1\nval x = f(1)\n"
        val claimed: (WContext) -> Unit = { claimCall(it, "f(1)") }

        visit(source, LayoutDirectiveKind.COLUMNS, prepare = claimed).map { it.message } shouldBe
            listOf("size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element")
        visit(source, LayoutDirectiveKind.MATRIX, prepare = claimed).map { it.message } shouldBe
            listOf("size 1 in '// wrasse matrix:1' is below the minimum of 2: a row needs at least 2 elements")
    }

    should("report an invalid columns size with the minimum of one") {
        val source = "// wrasse columns:abc\nval x = f(1)\n"

        visit(source, LayoutDirectiveKind.COLUMNS) { claimCall(it, "f(1)") }.map { it.message } shouldBe
            listOf("invalid size 'abc' in '// wrasse columns:abc': the size must be a whole number of at least 1")
    }

    should("report nothing for a claimed columns directive of size one") {
        visit("// wrasse columns:1\nval x = f(1)\n", LayoutDirectiveKind.COLUMNS) { claimCall(it, "f(1)") } shouldBe
            emptyList()
    }

    should("report the failure the printer recorded on a claimed directive") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"

        val reported = visit(source) {
            claimCall(it, "f(1)")
            it.layoutDirectives.all().single().fail("recorded failure")
        }

        reported.map { it.message } shouldBe listOf("recorded failure")
    }

    should("report nothing for a claimed directive the printer accepted") {
        visit("// wrasse matrix:2\nval x = f(1)\n") { claimCall(it, "f(1)") } shouldBe emptyList()
    }

    should("ignore a directive lying inside a fmt:off region, whatever its problem") {
        val source = "// wrasse matrix:abc\nval x = f(1)\n"

        visit(source) { it.formatRegions.addSpan(0, 10) } shouldBe emptyList()
    }

    should("ignore a directive whose target line starts inside a fmt:off region") {
        val source = "// wrasse matrix:abc\nval x = f(1)\n"

        visit(source) { it.formatRegions.addSpan(21, 30) } shouldBe emptyList()
    }

    should("judge a directive's region by the line past the directives and annotations below it") {
        val source = "// wrasse matrix:abc\n// wrasse columns:abc\nval x = f(1)\n"
        val target = source.indexOf("val x")

        visit(source) { it.formatRegions.addSpan(21, 30) } shouldBe
            listOf(
                Reported(
                    "format-matrix",
                    "invalid size 'abc' in '// wrasse matrix:abc': the size must be a whole number of at least 2",
                    0,
                    20,
                    RuleLevel.ERROR,
                    emptyList(),
                ),
            )
        visit(source) { it.formatRegions.addSpan(target, source.length) } shouldBe emptyList()
    }

    should("report every rejected directive in source order") {
        val source = "// wrasse matrix:1\na\n// wrasse matrix:2\nb\n"

        visit(source).map { it.start } shouldBe listOf(0, 21)
    }

    should("report a clash on each of its directives under that directive's own rule id") {
        val source = "// wrasse matrix:3\n// wrasse columns:3\nval x = f(1)\n"
        val clash = "'// wrasse matrix:3' (line 1) and '// wrasse columns:3' (line 2) both target the call below; " +
            "keep only one"
        val prepare = { ctx: WContext ->
            claimCall(ctx, "f(1)")
            ctx.layoutDirectives.all().forEach { it.fail(clash) }
        }

        visit(source, LayoutDirectiveKind.MATRIX, prepare = prepare) shouldBe
            listOf(Reported("format-matrix", clash, 0, 18, RuleLevel.ERROR, emptyList()))
        visit(source, LayoutDirectiveKind.COLUMNS, prepare = prepare) shouldBe
            listOf(Reported("format-columns", clash, 19, 38, RuleLevel.ERROR, emptyList()))
    }

    should("never attach an edit") {
        visit("// wrasse matrix:abc\nval x = f(1)\n").flatMap { it.edits } shouldBe emptyList()
    }
})
