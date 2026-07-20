package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

/**
 * Drives [DocBuilder] with hand-built SAX events — no compiler, no parse — proving the Doc IR
 * composition rules in isolation, the same way [WNodeTypeMappingCompletenessSpec]-style fixture
 * tests prove a real compile end to end. [WContext.type]/[WContext.leafText] are ordinary Kotlin
 * `var`s (the `@JvmSynthetic` on their setters only hides them from Java callers).
 */
class DocBuilderSpec : BaseSpec({

    fun formatConfig() = WFormatConfig(
        enabled = true,
        style = FormatStyle(indentWidth = 4),
        ruleConfig = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = RuleLevel.ERROR),
    )

    fun leaf(builder: DocBuilder, ctx: WContext, type: WNodeType, text: String) {
        ctx.type = type
        ctx.leafText = text
        builder.visitLeaf(ctx, noopReporter)
    }

    fun render(builder: DocBuilder, ctx: WContext): String {
        ctx.type = WNodeType.FILE
        ctx.sourceText = ""
        val recorder = RecordingReporter()
        builder.afterFile(ctx, recorder)
        return recorder.lastEdit?.replacement ?: error("DocBuilder reported no edit — rendered output equalled the (empty) placeholder source")
    }

    should("normalize interior indentation and dedent the line holding a block's own closing brace") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt2")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    stmt1\n    stmt2\n}"
    }

    should("not double-indent a lambda body: FUNCTION_LITERAL owns the one indent level, its interior BLOCK is transparent") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "name")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt2")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{ name ->\n    stmt1\n    stmt2\n}"
    }

    should("report no edit and no diagnostic when the rendered output already equals the source") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })
        ctx.sourceText = "x"

        val recorder = RecordingReporter()
        builder.afterFile(ctx, recorder)

        recorder.reports shouldBe emptyList()
        recorder.lastEdit shouldBe null
    }
})

private val noopReporter = object : WReporter {
    override val reports = mutableListOf<ViolationReport>()
    override fun report(
        ruleId: String,
        message: String,
        startOffset: Int,
        endOffset: Int,
        rule: WRule,
        edits: List<WEdit>,
    ) {
    }
}

private class RecordingReporter : WReporter {
    override val reports = mutableListOf<ViolationReport>()
    var lastEdit: WEdit? = null

    override fun report(
        ruleId: String,
        message: String,
        startOffset: Int,
        endOffset: Int,
        rule: WRule,
        edits: List<WEdit>,
    ) {
        reports.add(ViolationReport(message = message, startOffset = startOffset, endOffset = endOffset, level = rule.config.effectiveLevel))
        lastEdit = edits.firstOrNull()
    }
}
