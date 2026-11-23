package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FormatMatrixRuleSpec : BaseSpec({

    data class Reported(
        val ruleId: String,
        val message: String,
        val start: Int,
        val end: Int,
        val level: RuleLevel,
        val edits: List<WEdit>,
    )

    fun visit(
        source: String,
        level: RuleLevel = RuleLevel.ERROR,
        prepare: (WContext) -> Unit = {},
    ): List<Reported> {
        val ctx = WContext(filePath = "Sample.kt")
        ctx.sourceText = source
        var from = source.indexOf("// wrasse matrix:")
        while (from >= 0) {
            val lineEnd = source.indexOf('\n', from).let { if (it < 0) source.length else it }
            ctx.matrixDirectives.add(source.substring(from, lineEnd), from, lineEnd, from, source)
            from = source.indexOf("// wrasse matrix:", lineEnd)
        }
        prepare(ctx)
        val reported = mutableListOf<Reported>()
        val config = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = level)
        val rule = FormatMatrixRule(config)
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

    should("report a directive no call claimed at the directive comment under the rule id format-matrix") {
        val source = "val a = 1\n// wrasse matrix:2\nfun f() = 1\n"

        visit(source) shouldBe
            listOf(
                Reported(
                    "format-matrix",
                    "'// wrasse matrix:2' has no call to lay out: no call with a parenthesized argument list starts " +
                        "on the line directly below it (a function, constructor or class declaration is not a call)",
                    10,
                    28,
                    RuleLevel.ERROR,
                    emptyList(),
                ),
            )
    }

    should("report with the level of the format setting") {
        visit("// wrasse matrix:2\nfun f() = 1\n", level = RuleLevel.WARN).map { it.level } shouldBe
            listOf(RuleLevel.WARN)
    }

    should("report a size problem even for a claimed directive") {
        val source = "// wrasse matrix:abc\nval x = f(1)\n"

        visit(source) { it.matrixDirectives.claim(25) }.map { it.message } shouldBe
            listOf("invalid size 'abc' in '// wrasse matrix:abc': the size must be a whole number of at least 2")
    }

    should("report the failure the printer recorded on a claimed directive") {
        val source = "// wrasse matrix:2\nval x = f(1)\n"

        val reported = visit(source) {
            it.matrixDirectives.claim(25)
            it.matrixDirectives.all().single().fail("recorded failure")
        }

        reported.map { it.message } shouldBe listOf("recorded failure")
    }

    should("report nothing for a claimed directive the printer accepted") {
        visit("// wrasse matrix:2\nval x = f(1)\n") { it.matrixDirectives.claim(25) } shouldBe emptyList()
    }

    should("ignore a directive lying inside a fmt:off region, whatever its problem") {
        val source = "// wrasse matrix:abc\nval x = f(1)\n"

        visit(source) { it.formatRegions.addSpan(0, 10) } shouldBe emptyList()
    }

    should("ignore a directive whose line below starts inside a fmt:off region") {
        val source = "// wrasse matrix:abc\nval x = f(1)\n"

        visit(source) { it.formatRegions.addSpan(21, 30) } shouldBe emptyList()
    }

    should("report every rejected directive in source order") {
        val source = "// wrasse matrix:1\na\n// wrasse matrix:2\nb\n"

        visit(source).map { it.start } shouldBe listOf(0, 21)
    }

    should("never attach an edit") {
        visit("// wrasse matrix:abc\nval x = f(1)\n").flatMap { it.edits } shouldBe emptyList()
    }
})
