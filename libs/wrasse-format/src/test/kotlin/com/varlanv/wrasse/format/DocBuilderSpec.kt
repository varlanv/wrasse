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

    fun formatConfig(maxLineLength: Int = 140) = WFormatConfig(
        enabled = true,
        style = FormatStyle(indentWidth = 4, maxLineLength = maxLineLength),
        ruleConfig = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = RuleLevel.ERROR),
    )

    fun leaf(builder: DocBuilder, ctx: WContext, type: WNodeType, text: String) {
        ctx.type = type
        ctx.leafText = text
        builder.visitLeaf(ctx, noopReporter)
    }

    fun binaryPlus(builder: DocBuilder, ctx: WContext, lhs: String, rhs: String) {
        builder.enterNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, lhs)
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.PLUS, "+")
        builder.exitNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, rhs)
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
    }

    fun argumentList(builder: DocBuilder, ctx: WContext, args: List<String>) {
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        args.forEachIndexed { index, arg ->
            if (index > 0) {
                leaf(builder, ctx, WNodeType.COMMA, ",")
                leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
            }
            builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
            leaf(builder, ctx, WNodeType.IDENTIFIER, arg)
            builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        }
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
    }

    fun render(builder: DocBuilder, ctx: WContext): String {
        ctx.type = WNodeType.FILE
        ctx.sourceText = ""
        val recorder = RecordingReporter()
        builder.finish(ctx, recorder)
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

    should("collapse a dot chain that fits on one line, joining a hard-broken source") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "b")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "c")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "a.b().c()"
    }

    should("break a dot chain at every '.' when it exceeds maxLineLength, indenting continuations one level") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "abcdefghij")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "klm")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "nop")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "abcdefghij\n    .klm()\n    .nop()"
    }

    should("keep a short argument list flat inside a chain link whose own group is broken") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "abcdefghijklmnopqrstuvwxyz")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "1")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "2")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "bar")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "abcdefghijklmnopqrstuvwxyz\n    .foo(1, 2)\n    .bar()"
    }

    should("keep a short binary expression flat") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        binaryPlus(builder, ctx, "1", "2")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "1 + 2"
    }

    should("break a long binary expression after the operator, indenting the continuation one level") {
        val builder = DocBuilder(formatConfig(maxLineLength = 20))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        binaryPlus(builder, ctx, "aaaaaaaaaaaaaaaaaaaa", "b")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "aaaaaaaaaaaaaaaaaaaa +\n    b"
    }

    should("break a long elvis expression before the '?:', like a dot chain") {
        val builder = DocBuilder(formatConfig(maxLineLength = 20))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "aaaaaaaaaaaaaaaaaaaa")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.ELVIS, "?:")
        builder.exitNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "b")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "aaaaaaaaaaaaaaaaaaaa\n    ?: b"
    }

    should("keep a short argument list flat") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        argumentList(builder, ctx, listOf("1", "2", "3"))
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(1, 2, 3)"
    }

    should("break an argument list one arg per line when it exceeds maxLineLength, dedenting the closing paren") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        argumentList(builder, ctx, listOf("veryLongArgOne", "veryLongArgTwo"))
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\n    veryLongArgOne,\n    veryLongArgTwo\n)"
    }

    should("preserve an existing trailing comma without inserting a duplicate break before the closing paren") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "veryLongArgOne")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\n    veryLongArgOne,\n)"
    }

    should("report no edit and no diagnostic when the rendered output already equals the source") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })
        ctx.sourceText = "x"

        val recorder = RecordingReporter()
        builder.finish(ctx, recorder)

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
