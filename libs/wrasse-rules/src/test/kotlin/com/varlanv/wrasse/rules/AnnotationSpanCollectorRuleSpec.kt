package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class AnnotationSpanCollectorRuleSpec : BaseSpec({

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

    fun enter(
        ctx: WContext,
        rule: AnnotationSpanCollectorRule,
        source: String,
        entry: String,
    ): Boolean {
        val start = source.indexOf(entry)
        ctx.type = WNodeType.ANNOTATION_ENTRY
        ctx.startOffset = start
        ctx.endOffset = start + entry.length
        return rule.enterNode(ctx, reporter)
    }

    should("target annotation entries only") {
        AnnotationSpanCollectorRule().targetTypes shouldBe setOf(WNodeType.ANNOTATION_ENTRY)
    }

    should("record the span of each entry it enters so its line counts as annotation-only") {
        val source = "@JvmField\n@Suppress(\"X\")  @A\nval x = 1\n"
        val ctx = WContext(filePath = "Sample.kt")
        ctx.sourceText = source
        val rule = AnnotationSpanCollectorRule()

        enter(ctx, rule, source, "@JvmField")
        enter(ctx, rule, source, "@Suppress(\"X\")")
        enter(ctx, rule, source, "@A")

        ctx.annotationSpans.onlyOnLine(source, 0, 9) shouldBe true
        ctx.annotationSpans.onlyOnLine(source, 10, 28) shouldBe true
        ctx.annotationSpans.onlyOnLine(source, 29, 38) shouldBe false
    }

    should("not stay active after entering a node") {
        val source = "@A\nval x = 1\n"
        val ctx = WContext(filePath = "Sample.kt")

        enter(ctx, AnnotationSpanCollectorRule(), source, "@A") shouldBe false
    }
})
