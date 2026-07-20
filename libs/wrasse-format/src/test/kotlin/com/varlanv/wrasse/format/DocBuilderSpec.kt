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

    fun binaryOp(builder: DocBuilder, ctx: WContext, lhs: String, opType: WNodeType, opText: String, rhs: String) {
        builder.enterNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, lhs)
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, opType, opText)
        builder.exitNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, rhs)
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
    }

    fun binaryPlus(builder: DocBuilder, ctx: WContext, lhs: String, rhs: String) =
        binaryOp(builder, ctx, lhs, WNodeType.PLUS, "+", rhs)

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

    should("strip the space before a property's type-annotation colon and enforce one space after") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Int")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val x: Int"
    }

    should("space both sides of a class declaration's supertype-list colon") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Bar")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo : Bar"
    }

    should("space both sides of a generic type parameter's bound colon") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "T")
        leaf(builder, ctx, WNodeType.COLON, ":")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Any")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_PARAMETER })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "T : Any"
    }

    should("keep angle brackets tight for a type argument list but spaced for a comparison operator") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LT, "<")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Int")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.GT, ">")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        binaryOp(builder, ctx, "a", WNodeType.LT, "<", "b")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "<Int>\na < b"
    }

    should("keep a unary operator tight to its operand but a binary operator of the same token spaced") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PREFIX_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.MINUS, "-")
        builder.exitNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.PREFIX_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        binaryOp(builder, ctx, "a", WNodeType.MINUS, "-", "b")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "-x\na - b"
    }

    should("normalize comma spacing in a declaration-site parameter list: none before, one after") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "b")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(a, b)"
    }

    should("insert exactly one space after a control-flow keyword regardless of what follows") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.IF })
        leaf(builder, ctx, WNodeType.KW_IF, "if")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.IF })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "if (x)"
    }

    should("space out a non-empty single-line lambda's braces but collapse a truly empty one") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "   ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{ x }\n{}"
    }

    should("keep a nullable type's '?' tight to the preceding type") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.NULLABLE_TYPE })
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Int")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.QUEST, "?")
        builder.exitNode(ctx.apply { type = WNodeType.NULLABLE_TYPE })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "Int?"
    }

    should("tighten the gap after '::' but preserve whatever the source had before it") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "  ")
        leaf(builder, ctx, WNodeType.COLONCOLON, "::")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "class")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "Foo  ::class"
    }

    should("keep the range operator tight both sides") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RANGE, "..")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "5")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "1..5"
    }

    should("keep a spread operator's '*' tight to its argument") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.MUL, "*")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "array")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "*array"
    }

    should("keep a lambda's own parameter list spaced from '{', not tightened by the name-before-param-list rule") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "names")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "forEach")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.LAMBDA_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "name")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        builder.exitNode(ctx.apply { type = WNodeType.LAMBDA_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "names.forEach { name ->\n    stmt\n}"
    }

    should("keep square brackets tight inside") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.LBRACKET, "[")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "0")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACKET, "]")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "[0]"
    }

    should("insert a space before 'where', normalize the space after it, and space its constraint colons") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.KW_WHERE, "where")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "  ")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_CONSTRAINT_LIST })
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_CONSTRAINT })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "T")
        leaf(builder, ctx, WNodeType.COLON, ":")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Any")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_REFERENCE })
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_CONSTRAINT })
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_CONSTRAINT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun foo() where T : Any"
    }

    should("cap more than one blank line down to exactly one, anywhere in a block") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n\n\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt2")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    stmt1\n\n    stmt2\n}"
    }

    should("strip a blank line immediately before a closing brace") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    stmt1\n}"
    }

    should("strip the first blank line inside a function's own body block") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun foo() {\n    stmt\n}"
    }

    should("strip the first blank line inside a class body, unconditionally") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS_BODY })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS_BODY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    stmt\n}"
    }

    should("preserve a lambda body's own first blank line — FUNCTION_LITERAL is not in scope of no-empty-first-line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        builder.enterNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "stmt")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun foo() {\n    {\n\n        stmt\n    }\n}"
    }

    should("remove a blank line between a class name and its explicit primary constructor") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n")
        builder.enterNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        leaf(builder, ctx, WNodeType.KW_CONSTRUCTOR, "constructor")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo\nconstructor()"
    }

    should("force exactly one blank line after the package directive and after a non-empty import list") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PACKAGE_DIRECTIVE })
        leaf(builder, ctx, WNodeType.KW_PACKAGE, "package")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "sample")
        builder.exitNode(ctx.apply { type = WNodeType.PACKAGE_DIRECTIVE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.IMPORT_LIST })
        builder.enterNode(ctx.apply { type = WNodeType.IMPORT_DIRECTIVE })
        leaf(builder, ctx, WNodeType.KW_IMPORT, "import")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "kotlin")
        builder.exitNode(ctx.apply { type = WNodeType.IMPORT_DIRECTIVE })
        builder.exitNode(ctx.apply { type = WNodeType.IMPORT_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "package sample\n\nimport kotlin\n\nfun foo"
    }

    should("never force a blank line around an empty import list") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PACKAGE_DIRECTIVE })
        leaf(builder, ctx, WNodeType.KW_PACKAGE, "package")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "sample")
        builder.exitNode(ctx.apply { type = WNodeType.PACKAGE_DIRECTIVE })
        builder.enterNode(ctx.apply { type = WNodeType.IMPORT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.IMPORT_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "package sample\nfun foo"
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
