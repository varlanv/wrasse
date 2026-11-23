package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.FormatRegions
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FormatDirectiveCollectorRuleSpec : BaseSpec({

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

    fun collect(source: String): List<Pair<Int, Int>> {
        val ctx = WContext(filePath = "Sample.kt")
        ctx.sourceText = source
        val rule = FormatDirectiveCollectorRule()
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
        return ctx.formatRegions.all().map { it.start to it.end }
    }

    should("record a region from the off line start to the on comment end") {
        val source = "a\n    // wrasse fmt:off\nb\n    // wrasse fmt:on\nc\n"

        collect(source) shouldBe listOf(2 to source.indexOf("\nc"))
    }

    should("record a directive at the very start of the file") {
        val source = "// wrasse fmt:off\nb\n// wrasse fmt:on"

        collect(source) shouldBe listOf(0 to source.length)
    }

    should("leave an unclosed off open-ended") {
        collect("a\n// wrasse fmt:off\nb\n") shouldBe listOf(2 to FormatRegions.OPEN_END)
    }

    should("accept tab indentation before a directive") {
        val source = "a\n\t \t// wrasse fmt:off\nb\n\t// wrasse fmt:on\n"

        collect(source) shouldBe listOf(2 to source.indexOf("\n", source.indexOf("fmt:on")))
    }

    should("ignore an on with no open region") {
        collect("a\n// wrasse fmt:on\nb\n") shouldBe emptyList()
    }

    should("ignore a directive that shares its line with code") {
        collect("val a = 1 // wrasse fmt:off\nval b = 2 // wrasse fmt:on\n") shouldBe emptyList()
    }

    should("ignore a directive that follows a block comment on its line") {
        collect("/* x */ // wrasse fmt:off\nb\n// wrasse fmt:on\n") shouldBe emptyList()
    }

    should("ignore a directive with a carriage return in front of the comment") {
        collect("a\n\r// wrasse fmt:off\nb\n// wrasse fmt:on\n") shouldBe emptyList()
    }

    should("ignore every near-miss directive text") {
        val nearMisses = listOf(
            "//wrasse fmt:off",
            "// wrasse  fmt:off",
            "// wrasse fmt:off ",
            "// wrasse\tfmt:off",
            "// WRASSE FMT:OFF",
            "// wrasse fmt:off because",
            "//\twrasse fmt:off",
        )
        for (nearMiss in nearMisses) {
            collect("a\n$nearMiss\nb\n// wrasse fmt:on\n") shouldBe emptyList()
        }
    }
})
