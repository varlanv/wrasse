package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.model.LayoutDirectiveKind
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WCallArgument
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WResolvedUsage
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Drives [DocBuilder] with hand-built SAX events — no compiler, no parse — proving the Doc IR
 * composition rules in isolation, the same way [WNodeTypeMappingCompletenessSpec]-style fixture
 * tests prove a real compile end to end. [WContext.type]/[WContext.leafText] are ordinary Kotlin
 * `var`s (the `@JvmSynthetic` on their setters only hides them from Java callers).
 */
class DocBuilderSpec : BaseSpec({

    fun formatConfig(
        maxLineLength: Int = 140,
        multilineSignatureThreshold: Int? = null,
        trailingCommas: Boolean = true,
    ) = WFormatConfig(
        enabled = true,
        style = FormatStyle(
            indentWidth = 4,
            maxLineLength = maxLineLength,
            multilineSignatureThreshold = multilineSignatureThreshold,
            trailingCommas = trailingCommas,
        ),
        ruleConfig = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = RuleLevel.ERROR),
    )

    fun leaf(
        builder: DocBuilder,
        ctx: WContext,
        type: WNodeType,
        text: String,
    ) {
        ctx.type = type
        ctx.leafText = text
        builder.visitLeaf(ctx, noopReporter)
    }

    fun binaryOp(
        builder: DocBuilder,
        ctx: WContext,
        lhs: String,
        opType: WNodeType,
        opText: String,
        rhs: String,
    ) {
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

    fun binaryPlus(
        builder: DocBuilder,
        ctx: WContext,
        lhs: String,
        rhs: String,
    ) = binaryOp(builder, ctx, lhs, WNodeType.PLUS, "+", rhs)

    fun argumentList(
        builder: DocBuilder,
        ctx: WContext,
        args: List<String>,
    ) {
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

    fun valueParameter(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
        typeName: String,
        body: (() -> Unit)? = null,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        if (body != null) {
            body()
        } else {
            builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
            leaf(builder, ctx, WNodeType.IDENTIFIER, typeName)
            builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        }
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
    }

    fun funWithParameterList(
        builder: DocBuilder,
        ctx: WContext,
        buildParams: () -> Unit,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "f")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        buildParams()
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
    }

    fun funWithParameters(
        builder: DocBuilder,
        ctx: WContext,
        params: List<Pair<String, String>>,
    ) {
        funWithParameterList(builder, ctx) {
            params.forEachIndexed { index, (name, typeName) ->
                if (index > 0) {
                    leaf(builder, ctx, WNodeType.COMMA, ",")
                    leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
                }
                valueParameter(builder, ctx, name, typeName)
            }
        }
    }

    fun render(builder: DocBuilder, ctx: WContext): String {
        ctx.type = WNodeType.FILE
        ctx.sourceText = ""
        val recorder = RecordingReporter()
        builder.finish(ctx, recorder)
        return recorder.lastEdit?.replacement
            ?: error("DocBuilder reported no edit — rendered output equalled the (empty) placeholder source")
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

    should(
        "not double-indent a lambda body: FUNCTION_LITERAL owns the one indent level, its interior BLOCK is transparent",
    ) {
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

    should(
        "wrapRoot: a chain whose receiver is a raw multi-line string keeps its first link on the closing quotes' line",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n    line one\n    line two\n")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "trimIndent")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n    line one\n    line two\n\"\"\".trimIndent()"
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

        render(builder, ctx) shouldBe "(\n    veryLongArgOne,\n    veryLongArgTwo,\n)"
    }

    fun varargCallSite(
        argumentCount: Int,
        parameterCount: Int = 1,
    ) = WCallSite(
        callStartOffset = 0,
        callEndOffset = LIST_END,
        calleePackageFqName = "sample",
        calleeClassFqName = null,
        calleeName = "f",
        hasStableParameterNames = true,
        arguments = List(argumentCount) { WCallArgument(it * 2 + 1, it * 2 + 2, "xs", isVararg = true) },
        parameterCount = parameterCount,
        hasOnlyVarargParameter = parameterCount == 1,
    )

    fun resolvedUsageOf(
        vararg sites: WCallSite,
    ) = WResolvedUsage(
        classifiers = emptySet(),
        callables = emptySet(),
        hasResolutionErrors = false,
        resolvedImports = emptyList(),
        callSites = sites.toList(),
    )

    fun renderCall(
        usage: WResolvedUsage?,
        args: List<String>,
        spreadFirst: Boolean = false,
    ): String {
        val builder = DocBuilder(formatConfig(maxLineLength = 15))
        val ctx = WContext(filePath = "test.kt")
        ctx.resolvedUsage = usage
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        args.forEachIndexed { index, arg ->
            if (index > 0) {
                leaf(builder, ctx, WNodeType.COMMA, ",")
                leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
            }
            builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
            if (spreadFirst && index == 0) leaf(builder, ctx, WNodeType.MUL, "*")
            leaf(builder, ctx, WNodeType.IDENTIFIER, arg)
            builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        }
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(
            ctx.apply {
                type = WNodeType.VALUE_ARGUMENT_LIST
                startOffset = 0
                endOffset = LIST_END
            },
        )
        builder.exitNode(ctx.apply { type = WNodeType.FILE })
        return render(builder, ctx)
    }

    should("fill the elements of a broken argument list whose call resolves to a lone vararg parameter") {
        val usage = resolvedUsageOf(varargCallSite(argumentCount = 3))

        renderCall(usage, listOf("aaaa", "bbbb", "cccc")) shouldBe "(\n    aaaa, bbbb,\n    cccc,\n)"
    }

    should("keep one argument per line when no resolved call matches the argument list") {
        renderCall(null, listOf("aaaa", "bbbb", "cccc")) shouldBe "(\n    aaaa,\n    bbbb,\n    cccc,\n)"
    }

    should("keep one argument per line when the callee declares a parameter besides the vararg") {
        val usage = resolvedUsageOf(varargCallSite(argumentCount = 3, parameterCount = 2))

        renderCall(usage, listOf("aaaa", "bbbb", "cccc")) shouldBe "(\n    aaaa,\n    bbbb,\n    cccc,\n)"
    }

    should("keep one argument per line when an argument is spread") {
        val usage = resolvedUsageOf(varargCallSite(argumentCount = 3))

        renderCall(usage, listOf("aaaa", "bbbb", "cccc"), spreadFirst = true) shouldBe
            "(\n    *aaaa,\n    bbbb,\n    cccc,\n)"
    }

    should("keep a fitting argument list of a lone vararg call on one line") {
        val usage = resolvedUsageOf(varargCallSite(argumentCount = 3))

        renderCall(usage, listOf("a", "b", "c")) shouldBe "(a, b, c)"
    }

    fun matrixSite(
        argumentCount: Int,
        hasOnlyVarargParameter: Boolean = true,
        callEnd: Int = LIST_END,
    ) = WCallSite(
        callStartOffset = 0,
        callEndOffset = callEnd,
        calleePackageFqName = "sample",
        calleeClassFqName = null,
        calleeName = "table",
        hasStableParameterNames = true,
        arguments = List(argumentCount) { WCallArgument(it * 2 + 1, it * 2 + 2, "xs", isVararg = true) },
        parameterCount = if (hasOnlyVarargParameter) 1 else 2,
        hasOnlyVarargParameter = hasOnlyVarargParameter,
    )

    class MatrixRun(
        val rendered: String,
        val ctx: WContext,
        val listStart: Int,
    ) {
        val directives get() = ctx.layoutDirectives.all()
        val directive get() = directives.last()
    }

    fun plainArgument(
        builder: DocBuilder,
        ctx: WContext,
        text: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.IDENTIFIER, text)
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
    }

    fun plainArguments(vararg texts: String): List<(DocBuilder, WContext) -> Unit> = texts.map { text ->
        { builder: DocBuilder, ctx: WContext -> plainArgument(builder, ctx, text) }
    }

    fun matrixCall(
        directiveText: String?,
        arguments: List<(DocBuilder, WContext) -> Unit>,
        site: WCallSite? = matrixSite(arguments.size),
        trailingLambda: Boolean = false,
        trailingCommas: Boolean = true,
        regionOverDirective: Boolean = false,
        separator: (DocBuilder, WContext) -> Unit = { _, _ -> },
        above: List<String> = emptyList(),
    ): MatrixRun {
        val builder = DocBuilder(formatConfig(maxLineLength = 15, trailingCommas = trailingCommas))
        val ctx = WContext(filePath = "test.kt")
        ctx.resolvedUsage = site?.let { resolvedUsageOf(it) }
        val header = (above + listOfNotNull(directiveText)).joinToString("") { "$it\n" }
        val listStart = header.length + 5
        ctx.sourceText = header + "x".repeat(LIST_END - header.length)
        var lineStart = 0
        for (text in above + listOfNotNull(directiveText)) {
            val kind = checkNotNull(LayoutDirectiveKind.ofText(text))
            ctx.layoutDirectives.add(kind, text, lineStart, lineStart + text.length, lineStart, ctx.sourceText)
            lineStart += text.length + 1
        }
        if (regionOverDirective) ctx.formatRegions.addSpan(0, 5)
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        ctx.startOffset = CALLEE_START
        ctx.endOffset = CALLEE_START + 5
        leaf(builder, ctx, WNodeType.IDENTIFIER, "table")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(
            ctx.apply {
                type = WNodeType.VALUE_ARGUMENT_LIST
                startOffset = listStart
                endOffset = LIST_END
            },
        )
        leaf(builder, ctx, WNodeType.LPAR, "(")
        separator(builder, ctx)
        arguments.forEachIndexed { index, argument ->
            if (index > 0) {
                leaf(builder, ctx, WNodeType.COMMA, ",")
                leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
            }
            argument(builder, ctx)
        }
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(
            ctx.apply {
                type = WNodeType.VALUE_ARGUMENT_LIST
                startOffset = listStart
                endOffset = LIST_END
            },
        )
        if (trailingLambda) {
            builder.enterNode(ctx.apply { type = WNodeType.LAMBDA_ARGUMENT })
            leaf(builder, ctx, WNodeType.LBRACE, "{")
            leaf(builder, ctx, WNodeType.RBRACE, "}")
            builder.exitNode(ctx.apply { type = WNodeType.LAMBDA_ARGUMENT })
        }
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })
        val recorder = RecordingReporter()
        builder.finish(ctx, recorder)
        return MatrixRun(recorder.lastEdit?.replacement ?: error("DocBuilder reported no edit"), ctx, listStart)
    }

    val directive2 = "// wrasse matrix:2"

    should("lay the arguments of a claimed vararg call out in aligned rows of the directive size") {
        val run = matrixCall(directive2, plainArguments("aa", "b", "c", "ddd"))

        run.rendered shouldBe "table(\n    aa, b,\n    c,  ddd,\n)"
        run.directive.claimed shouldBe true
        run.directive.failure shouldBe null
    }

    should("lay a matrix out although the list would fit on one line") {
        val run = matrixCall("// wrasse matrix:2", plainArguments("a", "b"))

        run.rendered shouldBe "table(\n    a, b,\n)"
    }

    should("lay a matrix out of one row when the size equals the argument count") {
        matrixCall("// wrasse matrix:4", plainArguments("a", "bb", "c", "d")).rendered shouldBe
            "table(\n    a, bb, c, d,\n)"
    }

    should("end the last row without a comma when trailing commas are off") {
        matrixCall(directive2, plainArguments("a", "b", "c", "d"), trailingCommas = false).rendered shouldBe
            "table(\n    a, b,\n    c, d\n)"
    }

    should("keep an empty argument list empty and skip the count checks") {
        val empty = matrixCall("// wrasse matrix:7", emptyList(), site = matrixSite(0))

        empty.rendered shouldBe "table()"
        empty.directive.failure shouldBe null
    }

    should("join an empty argument list written over several lines") {
        val run = matrixCall(
            directive2,
            emptyList(),
            site = matrixSite(0),
            separator = { builder, ctx -> leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ") },
        )

        run.rendered shouldBe "table()"
    }

    should("keep the ordinary layout of a call nothing targets") {
        matrixCall(null, plainArguments("aa", "b")).rendered shouldBe "table(aa, b)"
    }

    should("record each way a directive is rejected and leave the call to the ordinary layout") {
        fun failureOf(
            arguments: List<(DocBuilder, WContext) -> Unit>,
            site: WCallSite? = matrixSite(arguments.size),
            trailingLambda: Boolean = false,
        ): String? = matrixCall(directive2, arguments, site, trailingLambda).directive.failure

        val four = plainArguments("a", "b", "c", "d")
        val target = matrixCall(directive2, four).directive

        failureOf(four, site = null) shouldBe LayoutDirectiveMessages.unresolvedCallee(target)
        failureOf(four, site = matrixSite(4, hasOnlyVarargParameter = false)) shouldBe
            LayoutDirectiveMessages.notSingleVarargCallee(target, "table")
        failureOf(four, trailingLambda = true) shouldBe LayoutDirectiveMessages.trailingLambda(target)
        failureOf(four, site = matrixSite(4, callEnd = LIST_END + 5)) shouldBe
            LayoutDirectiveMessages.unresolvedCallee(target)
        failureOf(plainArguments("a", "b", "c")) shouldBe LayoutDirectiveMessages.countNotDivisible(target, 2, 3)
        failureOf(plainArguments("a")) shouldBe LayoutDirectiveMessages.sizeExceedsCount(target, 2, 1)
    }

    should("keep the call of an invalid size verbatim and leave the size problem to the rule") {
        val run = matrixCall("// wrasse matrix:abc", plainArguments("a", "b", "c", "d"))

        run.directive.failure shouldBe null
        run.directive.claimed shouldBe true
        LayoutDirectiveMessages.problemOf(run.directive) shouldBe LayoutDirectiveMessages.sizeProblem(run.directive)
        run.ctx.formatRegions.all().map { it.start to it.end } shouldBe listOf(run.listStart to LIST_END)
    }

    should("reject a spread argument") {
        val spread = { builder: DocBuilder, ctx: WContext ->
            builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
            leaf(builder, ctx, WNodeType.MUL, "*")
            leaf(builder, ctx, WNodeType.IDENTIFIER, "xs")
            builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        }
        val run = matrixCall(directive2, listOf(spread) + plainArguments("b"))

        run.directive.failure shouldBe LayoutDirectiveMessages.spreadArgument(run.directive)
    }

    should("reject a named argument") {
        val named = { builder: DocBuilder, ctx: WContext ->
            builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
            leaf(builder, ctx, WNodeType.IDENTIFIER, "xs")
            leaf(builder, ctx, WNodeType.EQ, "=")
            leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
            builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        }
        val run = matrixCall(directive2, listOf(named) + plainArguments("b"))

        run.directive.failure shouldBe LayoutDirectiveMessages.namedArgument(run.directive)
    }

    should("reject a comment between the arguments") {
        val comment = { builder: DocBuilder, ctx: WContext ->
            leaf(builder, ctx, WNodeType.BLOCK_COMMENT, "/* c */")
        }
        val run = matrixCall(directive2, plainArguments("a") + comment + plainArguments("b"))

        run.directive.failure shouldBe LayoutDirectiveMessages.comment(run.directive)
    }

    should("reject an element that cannot be rendered on one line") {
        val multiline = { builder: DocBuilder, ctx: WContext ->
            builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
            builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
            leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
            leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
            leaf(builder, ctx, WNodeType.IDENTIFIER, "b")
            builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
            builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        }
        val run = matrixCall(directive2, listOf(multiline) + plainArguments("c"))

        run.directive.failure shouldBe LayoutDirectiveMessages.multilineElement(run.directive)
    }

    should("keep a rejected call's argument list verbatim by adding its span as a region") {
        val run = matrixCall(directive2, plainArguments("a", "b", "c"))

        run.ctx.formatRegions.all().map { it.start to it.end } shouldBe listOf(run.listStart to LIST_END)
    }

    should("add no region for an accepted call") {
        matrixCall(directive2, plainArguments("a", "b", "c", "d")).ctx.formatRegions.isEmpty shouldBe true
    }

    val columns3 = "// wrasse columns:3"

    should("lay a columns call out in rows of the directive size ending on a short row") {
        matrixCall(columns3, plainArguments("a", "bb", "c", "d", "e", "ff", "g", "h")).rendered shouldBe
            "table(\n    a, bb, c,\n    d, e,  ff,\n    g, h,\n)"
        matrixCall(columns3, plainArguments("a", "b", "c", "d", "e", "f", "g")).rendered shouldBe
            "table(\n    a, b, c,\n    d, e, f,\n    g,\n)"
    }

    should("lay a columns call out as one short row when it has fewer elements than columns") {
        matrixCall("// wrasse columns:5", plainArguments("a", "bb", "c")).rendered shouldBe "table(\n    a, bb, c,\n)"
    }

    should("lay a columns call of a single element out on its own line") {
        matrixCall("// wrasse columns:2", plainArguments("a")).rendered shouldBe "table(\n    a,\n)"
    }

    should("lay a columns call out exactly as the matrix of the same size when the rows fill up") {
        val arguments = plainArguments("aa", "b", "c", "ddd", "e", "f")

        matrixCall(columns3, arguments).rendered shouldBe matrixCall("// wrasse matrix:3", arguments).rendered
    }

    should("end the last row of a columns call without a comma when trailing commas are off") {
        matrixCall(columns3, plainArguments("a", "b", "c", "d"), trailingCommas = false).rendered shouldBe
            "table(\n    a, b, c,\n    d\n)"
    }

    should("lay a columns call of size one out with one element per row") {
        matrixCall("// wrasse columns:1", plainArguments("a", "bb", "c")).rendered shouldBe
            "table(\n    a,\n    bb,\n    c,\n)"
        matrixCall("// wrasse columns:1", plainArguments("a")).rendered shouldBe "table(\n    a,\n)"
        matrixCall("// wrasse columns:1", plainArguments("a", "b"), trailingCommas = false).rendered shouldBe
            "table(\n    a,\n    b\n)"
    }

    should("keep an empty argument list of a columns directive of size one empty") {
        val empty = matrixCall("// wrasse columns:1", emptyList(), site = matrixSite(0))

        empty.rendered shouldBe "table()"
        empty.directive.failure shouldBe null
        empty.ctx.formatRegions.isEmpty shouldBe true
    }

    should("keep the call of a columns directive of size zero verbatim and a matrix directive of size one too") {
        for (text in listOf("// wrasse columns:0", "// wrasse columns:-1", "// wrasse matrix:1")) {
            val run = matrixCall(text, plainArguments("a", "b", "c", "d"))

            run.directive.failure shouldBe null
            LayoutDirectiveMessages.problemOf(run.directive) shouldBe LayoutDirectiveMessages.sizeProblem(run.directive)
            run.ctx.formatRegions.all().map { it.start to it.end } shouldBe listOf(run.listStart to LIST_END)
        }
    }

    should("lay the call out by a columns directive of size one when a matrix directive of size one is stacked above") {
        val run = matrixCall("// wrasse columns:1", plainArguments("a", "b"), above = listOf("// wrasse matrix:1"))

        run.rendered shouldBe "table(\n    a,\n    b,\n)"
        run.directives.map { it.failure } shouldBe listOf(null, null)
    }

    should("fail a clash of a columns directive of size one and a matrix directive") {
        val run = matrixCall("// wrasse columns:1", plainArguments("a", "b"), above = listOf("// wrasse matrix:2"))
        val message = "'// wrasse matrix:2' (line 1) and '// wrasse columns:1' (line 2) both target the call below; " +
            "keep only one"

        run.directives.map { it.failure } shouldBe listOf(message, message)
    }

    should("keep an empty argument list of a columns directive empty") {
        val empty = matrixCall("// wrasse columns:9", emptyList(), site = matrixSite(0))

        empty.rendered shouldBe "table()"
        empty.directive.failure shouldBe null
    }

    should("accept every count for a columns directive that a matrix directive rejects") {
        val three = plainArguments("a", "b", "c")

        matrixCall("// wrasse columns:2", three).directive.failure shouldBe null
        matrixCall("// wrasse columns:4", three).directive.failure shouldBe null
        matrixCall("// wrasse matrix:2", three).directive.failure shouldNotBe null
        matrixCall("// wrasse matrix:4", three).directive.failure shouldNotBe null
    }

    should("reject a columns call the way a matrix call is rejected, naming the columns directive") {
        val four = plainArguments("a", "b", "c", "d")
        val target = matrixCall(columns3, four).directive

        matrixCall(columns3, four, site = null).directive.failure shouldBe
            "'// wrasse columns:3' targets a call whose callee the compiler could not resolve"
        matrixCall(columns3, four, trailingLambda = true).directive.failure shouldBe
            LayoutDirectiveMessages.trailingLambda(target)
        matrixCall(columns3, four, site = matrixSite(4, hasOnlyVarargParameter = false)).directive.failure shouldBe
            LayoutDirectiveMessages.notSingleVarargCallee(target, "table")
    }

    should("fail every directive of a clash with one message and keep the call verbatim") {
        val run = matrixCall(columns3, plainArguments("a", "b", "c", "d"), above = listOf("// wrasse matrix:2"))
        val message = "'// wrasse matrix:2' (line 1) and '// wrasse columns:3' (line 2) both target the call below; " +
            "keep only one"

        run.directives.map { it.failure } shouldBe listOf(message, message)
        run.directives.map { it.claimed } shouldBe listOf(true, true)
        run.ctx.formatRegions.all().map { it.start to it.end } shouldBe listOf(run.listStart to LIST_END)
    }

    should("name all three directives of a stacked clash") {
        val run = matrixCall(
            "// wrasse matrix:3",
            plainArguments("a", "b", "c"),
            above = listOf("// wrasse columns:2", "// wrasse columns:4"),
        )
        val message = "'// wrasse columns:2' (line 1), '// wrasse columns:4' (line 2) and '// wrasse matrix:3' " +
            "(line 3) all target the call below; keep only one"

        run.directives.map { it.failure } shouldBe listOf(message, message, message)
    }

    should("lay the call out by the valid directive when an invalid one is stacked above it") {
        val run = matrixCall(directive2, plainArguments("a", "b", "c", "d"), above = listOf("// wrasse columns:abc"))

        run.rendered shouldBe "table(\n    a, b,\n    c, d,\n)"
        run.directives.map { it.failure } shouldBe listOf(null, null)
        run.ctx.formatRegions.isEmpty shouldBe true
    }

    should("keep the call verbatim when every stacked directive has an invalid size") {
        val run = matrixCall("// wrasse matrix:1", plainArguments("a", "b"), above = listOf("// wrasse columns:abc"))

        run.directives.map { it.failure } shouldBe listOf(null, null)
        run.ctx.formatRegions.all().map { it.start to it.end } shouldBe listOf(run.listStart to LIST_END)
    }

    should("ignore a directive lying inside a fmt:off region and lay the call out the ordinary way") {
        val run = matrixCall("// wrasse matrix:abc", plainArguments("aa", "b"), regionOverDirective = true)

        run.rendered shouldBe "table(aa, b)"
        run.directive.failure shouldBe null
    }

    should("lay a matrix out from a call claimed by the directive in front of it only once") {
        val run = matrixCall(directive2, plainArguments("a", "b", "c", "d"))

        run.ctx.layoutDirectives.claim(run.listStart, run.ctx.sourceText, run.ctx.formatRegions) shouldBe null
    }

    should("leave a line comment that would turn into a directive when normalized exactly as written") {
        fun renderComment(text: String): String {
            val builder = DocBuilder(formatConfig())
            val ctx = WContext(filePath = "test.kt")
            builder.enterNode(ctx.apply { type = WNodeType.FILE })
            leaf(builder, ctx, WNodeType.EOL_COMMENT, text)
            builder.exitNode(ctx.apply { type = WNodeType.FILE })
            return render(builder, ctx)
        }

        listOf(
            "//wrasse matrix:2",
            "//wrasse matrix:",
            "//wrasse matrix:abc",
            "//wrasse columns:2",
            "//wrasse columns:",
            "//wrasse columns:abc",
            "//wrasse fmt:off",
            "//wrasse fmt:on",
        ).forEach { text -> renderComment(text) shouldBe text }
    }

    should("still normalize a line comment that does not turn into a directive") {
        fun renderComment(text: String): String {
            val builder = DocBuilder(formatConfig())
            val ctx = WContext(filePath = "test.kt")
            builder.enterNode(ctx.apply { type = WNodeType.FILE })
            leaf(builder, ctx, WNodeType.EOL_COMMENT, text)
            builder.exitNode(ctx.apply { type = WNodeType.FILE })
            return render(builder, ctx)
        }

        renderComment("//wrasse matrixes") shouldBe "// wrasse matrixes"
        renderComment("//wrasse fmt:off because") shouldBe "// wrasse fmt:off because"
        renderComment("//wrasse  matrix:2") shouldBe "// wrasse  matrix:2"
        renderComment("//Wrasse matrix:2") shouldBe "// Wrasse matrix:2"
        renderComment("//wrasse matrix 2") shouldBe "// wrasse matrix 2"
        renderComment("// wrasse matrix:2") shouldBe "// wrasse matrix:2"
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

    should("drop an existing trailing comma when joining an argument list back onto one line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(a)"
    }

    should("never emit a trailing comma when FormatStyle.trailingCommas is disabled, even for a broken argument list") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15, trailingCommas = false))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        argumentList(builder, ctx, listOf("veryLongArgOne", "veryLongArgTwo"))
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\n    veryLongArgOne,\n    veryLongArgTwo\n)"
    }

    should("strip an existing trailing comma when FormatStyle.trailingCommas is disabled") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15, trailingCommas = false))
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

        render(builder, ctx) shouldBe "(\n    veryLongArgOne\n)"
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

    should("remove a blank line between a class name and its primary constructor across a trailing comment") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// comment")
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

        render(builder, ctx) shouldBe "class Foo // comment\nconstructor()"
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

    should("force exactly one blank line after the package directive across a comment sitting before the import list") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PACKAGE_DIRECTIVE })
        leaf(builder, ctx, WNodeType.KW_PACKAGE, "package")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "sample")
        builder.exitNode(ctx.apply { type = WNodeType.PACKAGE_DIRECTIVE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// comment")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.IMPORT_LIST })
        builder.enterNode(ctx.apply { type = WNodeType.IMPORT_DIRECTIVE })
        leaf(builder, ctx, WNodeType.KW_IMPORT, "import")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "kotlin")
        builder.exitNode(ctx.apply { type = WNodeType.IMPORT_DIRECTIVE })
        builder.exitNode(ctx.apply { type = WNodeType.IMPORT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "package sample\n\n// comment\nimport kotlin"
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

    should("force a FUN's own parameter list one-per-line once the parameter count meets the threshold") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 2))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithParameters(builder, ctx, listOf("a" to "Int", "b" to "Int"))
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun f(\n    a: Int,\n    b: Int,\n)"
    }

    should("keep a FUN's own parameter list flat below the threshold when it fits") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 3))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithParameters(builder, ctx, listOf("a" to "Int", "b" to "Int"))
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun f(a: Int, b: Int)"
    }

    should("join an already wrapped FUN parameter list back onto one line once it fits below the threshold") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 3))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "f")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        valueParameter(builder, ctx, "a", "Int")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        valueParameter(builder, ctx, "b", "Int")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun f(a: Int, b: Int)"
    }

    should("break a FUN's own parameter list below the threshold when it exceeds maxLineLength") {
        val builder = DocBuilder(formatConfig(maxLineLength = 15, multilineSignatureThreshold = 3))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithParameters(builder, ctx, listOf("a" to "Int", "b" to "Int"))
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun f(\n    a: Int,\n    b: Int,\n)"
    }

    should("leave a secondary constructor's parameter list untouched regardless of parameter count or threshold") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 1))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.SECONDARY_CONSTRUCTOR })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        valueParameter(builder, ctx, "a", "Int")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        valueParameter(builder, ctx, "b", "Int")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.SECONDARY_CONSTRUCTOR })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(a: Int, b: Int)"
    }

    should(
        "force a PRIMARY_CONSTRUCTOR's own parameter list one-per-line once the parameter count meets the threshold",
    ) {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 2))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        valueParameter(builder, ctx, "a", "Int")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        valueParameter(builder, ctx, "b", "Int")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\n    a: Int,\n    b: Int,\n)"
    }

    should("bail on a FUN's parameter list containing a comment, preserving it verbatim") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 1))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithParameterList(builder, ctx) {
            valueParameter(builder, ctx, "a", "Int")
            leaf(builder, ctx, WNodeType.COMMA, ",")
            leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
            leaf(builder, ctx, WNodeType.EOL_COMMENT, "// note")
            leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
            valueParameter(builder, ctx, "b", "Int")
        }
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun f(a: Int, // note\nb: Int,)"
    }

    should("force a FUN's parameter list multiline when one parameter's own text already spans multiple lines") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 3))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithParameterList(builder, ctx) {
            valueParameter(builder, ctx, "message", "String") {
                builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
                leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
                leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\nhi\n")
                leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
                builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
            }
        }
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun f(\n    message: \"\"\"\nhi\n\"\"\",\n)"
    }

    should("add a static trailing comma to an already multi-line type parameter list") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LT, "<")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "T")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_PARAMETER })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.GT, ">")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "<\nT,\n>"
    }

    should("remove a stray trailing comma from a single-line type parameter list") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LT, "<")
        builder.enterNode(ctx.apply { type = WNodeType.TYPE_PARAMETER })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "T")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_PARAMETER })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.GT, ">")
        builder.exitNode(ctx.apply { type = WNodeType.TYPE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "<T>"
    }

    should("add a static trailing comma to an already multi-line destructuring declaration") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DESTRUCTURING_DECLARATION })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.DESTRUCTURING_DECLARATION_ENTRY })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
        builder.exitNode(ctx.apply { type = WNodeType.DESTRUCTURING_DECLARATION_ENTRY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.DESTRUCTURING_DECLARATION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\na,\n)"
    }

    should("add a static trailing comma to an already multi-line secondary constructor parameter list") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.SECONDARY_CONSTRUCTOR })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        valueParameter(builder, ctx, "a", "Int")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.SECONDARY_CONSTRUCTOR })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\n    a: Int,\n)"
    }

    should("add a dynamic trailing comma to a PRIMARY_CONSTRUCTOR's own parameter list forced multiline by threshold") {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 1))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        valueParameter(builder, ctx, "a", "Int")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(\n    a: Int,\n)"
    }

    should(
        "join a PRIMARY_CONSTRUCTOR's own parameter list back onto one line, dropping its comma, once it fits below the threshold",
    ) {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 3))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        valueParameter(builder, ctx, "a", "Int")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(a: Int)"
    }

    fun whenWithTwoConditions(builder: DocBuilder, ctx: WContext) {
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.WHEN })
        leaf(builder, ctx, WNodeType.KW_WHEN, "when")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "aa")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "bb")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })
    }

    should("pack a when entry's conditions onto one line, dropping the newline and the trailing comma") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        whenWithTwoConditions(builder, ctx)

        render(builder, ctx) shouldBe "when (x) {\n    aa, bb -> one\n}"
    }

    should("break a when entry's condition list before the condition whose arrow no longer fits") {
        val builder = DocBuilder(formatConfig(maxLineLength = 14))
        val ctx = WContext(filePath = "test.kt")
        whenWithTwoConditions(builder, ctx)

        render(builder, ctx) shouldBe "when (x) {\n    aa,\n    bb -> one\n}"
    }

    should("bail on a subject-less when's entry, leaving the break before its own arrow as written") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.WHEN })
        leaf(builder, ctx, WNodeType.KW_WHEN, "when")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "flag")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "yes")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "when {\n    flag\n    -> yes\n}"
    }

    fun classHeader(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
        buildParams: (() -> Unit)? = null,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        if (buildParams != null) {
            builder.enterNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
            builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
            leaf(builder, ctx, WNodeType.LPAR, "(")
            buildParams()
            leaf(builder, ctx, WNodeType.RPAR, ")")
            builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
            builder.exitNode(ctx.apply { type = WNodeType.PRIMARY_CONSTRUCTOR })
        }
    }

    fun superTypeEntry(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_ENTRY })
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_ENTRY })
    }

    should("keep a single supertype flat when it fits and the primary constructor did not wrap") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo : Bar"
    }

    should(
        "wrap a single supertype onto its own line when it does not fit, even though the primary constructor did not wrap",
    ) {
        val builder = DocBuilder(formatConfig(maxLineLength = 15))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "VeryLongSuperTypeName")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo :\n    VeryLongSuperTypeName"
    }

    should(
        "join a single supertype onto the constructor's closing line when the primary constructor is forced multiline",
    ) {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 1))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo") { valueParameter(builder, ctx, "a", "Int") }
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo(\n    a: Int,\n) : Bar"
    }

    should("keep supertypes flat when they fit on one line with the class header") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        superTypeEntry(builder, ctx, "Baz")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo : Bar, Baz"
    }

    should(
        "join only the first of two or more supertypes onto the constructor's closing line once it is forced multiline",
    ) {
        val builder = DocBuilder(formatConfig(multilineSignatureThreshold = 1))
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo") { valueParameter(builder, ctx, "a", "Int") }
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        superTypeEntry(builder, ctx, "Baz")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo(\n    a: Int,\n) : Bar,\n    Baz"
    }

    should("leave an OBJECT_DECLARATION's own supertype list untouched, never applying the CLASS-only wrap policy") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.OBJECT_DECLARATION })
        leaf(builder, ctx, WNodeType.KW_OBJECT, "object")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Config")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        superTypeEntry(builder, ctx, "Baz")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.OBJECT_DECLARATION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "object Config : Bar, Baz"
    }

    should("bail on a comment inside a class's supertype list, preserving it verbatim") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// keep")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo : Bar // keep"
    }

    should("bail on two supertypes that no comma separates, leaving the list as written") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        classHeader(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        superTypeEntry(builder, ctx, "Bar")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        superTypeEntry(builder, ctx, "Baz")
        builder.exitNode(ctx.apply { type = WNodeType.SUPER_TYPE_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo : Bar Baz"
    }

    fun bareAnnotationEntry(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.ANNOTATION_ENTRY })
        leaf(builder, ctx, WNodeType.AT, "@")
        builder.enterNode(ctx.apply { type = WNodeType.CONSTRUCTOR_CALLEE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        builder.exitNode(ctx.apply { type = WNodeType.CONSTRUCTOR_CALLEE })
        builder.exitNode(ctx.apply { type = WNodeType.ANNOTATION_ENTRY })
    }

    fun argumentedAnnotationEntry(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
        arg: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.ANNOTATION_ENTRY })
        leaf(builder, ctx, WNodeType.AT, "@")
        builder.enterNode(ctx.apply { type = WNodeType.CONSTRUCTOR_CALLEE })
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        builder.exitNode(ctx.apply { type = WNodeType.CONSTRUCTOR_CALLEE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, arg)
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.ANNOTATION_ENTRY })
    }

    fun funWithModifierList(
        builder: DocBuilder,
        ctx: WContext,
        buildEntries: () -> Unit,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        builder.enterNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
        buildEntries()
        builder.exitNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "f")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
    }

    should("leave a single argument-less annotation touching its declaration untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithModifierList(builder, ctx) { bareAnnotationEntry(builder, ctx, "Ann") }
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "@Ann fun f()"
    }

    should("force an argumented annotation onto its own line, pushing the declaration to the next line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithModifierList(builder, ctx) { argumentedAnnotationEntry(builder, ctx, "Ann", "1") }
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "@Ann(1)\nfun f()"
    }

    should("force two annotations onto separate lines even when neither has arguments") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithModifierList(builder, ctx) {
            bareAnnotationEntry(builder, ctx, "Ann1")
            leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
            bareAnnotationEntry(builder, ctx, "Ann2")
        }
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "@Ann1\n@Ann2\nfun f()"
    }

    should("leave an annotated value parameter's argumented annotation inline, never wrapping it") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        builder.enterNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
        argumentedAnnotationEntry(builder, ctx, "Ann", "1")
        builder.exitNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
        leaf(builder, ctx, WNodeType.COLON, ":")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Int")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "(@Ann(1) a: Int)"
    }

    should("leave an annotated expression immediately before a lambda expression untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.ANNOTATED_EXPRESSION })
        argumentedAnnotationEntry(builder, ctx, "Ann", "1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.LAMBDA_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.FUNCTION_LITERAL })
        builder.exitNode(ctx.apply { type = WNodeType.LAMBDA_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.ANNOTATED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "@Ann(1) { x }"
    }

    should("bail on an unrecognized annotation-array child inside a modifier list, preserving it verbatim") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        funWithModifierList(builder, ctx) {
            builder.enterNode(ctx.apply { type = WNodeType.UNKNOWN })
            leaf(builder, ctx, WNodeType.AT, "@")
            leaf(builder, ctx, WNodeType.LBRACKET, "[")
            leaf(builder, ctx, WNodeType.IDENTIFIER, "Ann1")
            leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
            leaf(builder, ctx, WNodeType.IDENTIFIER, "Ann2")
            leaf(builder, ctx, WNodeType.RBRACKET, "]")
            builder.exitNode(ctx.apply { type = WNodeType.UNKNOWN })
        }
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "@[Ann1 Ann2] fun f()"
    }

    fun minimalMultilineIf(
        builder: DocBuilder,
        ctx: WContext,
        bodyIdentifier: String = "a",
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.IF })
        leaf(builder, ctx, WNodeType.KW_IF, "if")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "cond")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n  ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, bodyIdentifier)
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.IF })
    }

    fun minimalSingleLineIf(builder: DocBuilder, ctx: WContext) {
        builder.enterNode(ctx.apply { type = WNodeType.IF })
        leaf(builder, ctx, WNodeType.KW_IF, "if")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "cond")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.IF })
    }

    should("convert a statement-separator semicolon between two same-line statements to a break, dropping it") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        leaf(builder, ctx, WNodeType.SEMICOLON, ";")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "two")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    one\n    two\n}"
    }

    should("convert a trailing statement-separator semicolon right before a block's own closing brace") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        leaf(builder, ctx, WNodeType.SEMICOLON, ";")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    one\n}"
    }

    should("never convert a semicolon directly followed by a comment") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        leaf(builder, ctx, WNodeType.SEMICOLON, ";")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// keep")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    one; // keep\n}"
    }

    should(
        "force a break after '{' and before '}' for a block already spanning multiple lines, with no semicolon involved",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "two")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    one\n    two\n}"
    }

    should("leave an entirely single-line block untouched — this mechanism never decides fit") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{ one }"
    }

    should("leave a single-line enum class body untouched — ktlint's own exemption") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS_BODY })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.ENUM_ENTRY })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "RED")
        builder.exitNode(ctx.apply { type = WNodeType.ENUM_ENTRY })
        leaf(builder, ctx, WNodeType.COMMA, ",")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.ENUM_ENTRY })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "BLUE")
        builder.exitNode(ctx.apply { type = WNodeType.ENUM_ENTRY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS_BODY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{ RED, BLUE }"
    }

    should("force own-line braces for a non-enum class body already spanning multiple lines") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS_BODY })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "a")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "b")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS_BODY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "{\n    a\n    b\n}"
    }

    should("force own-line braces for a WHEN construct, skipping past its own subject header") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.WHEN })
        leaf(builder, ctx, WNodeType.KW_WHEN, "when")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        builder.enterNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "ten")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        leaf(builder, ctx, WNodeType.KW_ELSE, "else")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "zero")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "when (x) {\n    one -> ten\n    else -> zero\n}"
    }

    should("keep a property's multi-line IF value on the = line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        minimalMultilineIf(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val x = if (cond) {\n    a\n}"
    }

    should("leave a property's single-line IF value untouched — not (yet) forced multi-line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        minimalSingleLineIf(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val x = if (cond) {}"
    }

    should(
        "never move a property's multiline raw string/lambda/object-literal value — outside MULTILINE_WRAPPABLE_VALUE_TYPES",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.REGULAR_STRING_PART, "\nline\n")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val x = \"\"\"\nline\n\"\"\""
    }

    should(
        "move a property's initializer onto its own line when a comment precedes the value, regardless of the value's own type",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// comment")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"")
        leaf(builder, ctx, WNodeType.REGULAR_STRING_PART, "foo")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val foo =\n    // comment\n    \"foo\""
    }

    should("keep a reassignment BINARY_EXPRESSION's multi-line IF value on the = line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.EQ, "=")
        builder.exitNode(ctx.apply { type = WNodeType.OPERATION_REFERENCE })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        minimalMultilineIf(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.BINARY_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "x = if (cond) {\n    a\n}"
    }

    should("keep a when-entry arrow's multi-line IF body on the arrow line") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.WHEN })
        leaf(builder, ctx, WNodeType.KW_WHEN, "when")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "one")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.ARROW, "->")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        minimalMultilineIf(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.WHEN_ENTRY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.WHEN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "when (x) {\n    one -> if (cond) {\n        a\n    }\n}"
    }

    fun bareClass(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
    }

    fun bareFun(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
    }

    fun simpleProperty(
        builder: DocBuilder,
        ctx: WContext,
        name: String,
        value: String,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, name)
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, value)
        builder.exitNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
    }

    should("force a blank line between two top-level classes with none in the source") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        bareClass(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        bareClass(builder, ctx, "Bar")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo\n\nclass Bar"
    }

    should("leave the first member of a class body untouched, never forcing a blank line right after '{'") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.CLASS_BODY })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        bareFun(builder, ctx, "bar")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS_BODY })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo {\n    fun bar() {}\n}"
    }

    should("leave the first statement of any block untouched, not only a function's own body") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.IF })
        leaf(builder, ctx, WNodeType.KW_IF, "if")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "cond")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        bareClass(builder, ctx, "Local")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.IF })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "if (cond) {\n    class Local\n}"
    }

    should("never force a blank line between two consecutive properties in a class body") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.CLASS_BODY })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        simpleProperty(builder, ctx, "a", "1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        simpleProperty(builder, ctx, "b", "2")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS_BODY })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo {\n    val a = 1\n    val b = 2\n}"
    }

    should("never force a blank line before a local property following an ordinary statement") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "demo")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "bar")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        simpleProperty(builder, ctx, "local", "1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun demo() {\n    bar\n    val local = 1\n}"
    }

    should(
        "force a blank line before a local FUN following a local property, unlike the property-only local exemption",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "outer")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.BLOCK })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        simpleProperty(builder, ctx, "x", "1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        bareFun(builder, ctx, "helper")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.BLOCK })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun outer() {\n    val x = 1\n\n    fun helper() {}\n}"
    }

    should(
        "attach a leading EOL comment run to the following declaration, forcing the blank line before the whole run",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        bareClass(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// one")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// two")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Bar")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo\n\n// one\n// two\nclass Bar"
    }

    should("attach a leading KDoc to the following declaration, forcing the blank line before it") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        bareFun(builder, ctx, "foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        builder.enterNode(ctx.apply { type = WNodeType.FUN })
        leaf(builder, ctx, WNodeType.KDOC, "/** doc */")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.KW_FUN, "fun")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "bar")
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.FUN })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "fun foo() {}\n\n/** doc */\nfun bar()"
    }

    should("force a blank line before an annotated property even though consecutive properties are otherwise exempt") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.CLASS })
        leaf(builder, ctx, WNodeType.KW_CLASS, "class")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.CLASS_BODY })
        leaf(builder, ctx, WNodeType.LBRACE, "{")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        simpleProperty(builder, ctx, "a", "1")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.enterNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
        bareAnnotationEntry(builder, ctx, "JvmField")
        builder.exitNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "b")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "2")
        builder.exitNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n")
        leaf(builder, ctx, WNodeType.RBRACE, "}")
        builder.exitNode(ctx.apply { type = WNodeType.CLASS_BODY })
        builder.exitNode(ctx.apply { type = WNodeType.CLASS })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo {\n    val a = 1\n\n    @JvmField\n    val b = 2\n}"
    }

    fun propertyAccessor(
        builder: DocBuilder,
        ctx: WContext,
        keyword: WNodeType,
        keywordText: String,
        annotated: Boolean,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY_ACCESSOR })
        if (annotated) {
            builder.enterNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
            bareAnnotationEntry(builder, ctx, "Ann")
            builder.exitNode(ctx.apply { type = WNodeType.MODIFIER_LIST })
            leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        }
        leaf(builder, ctx, keyword, keywordText)
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_PARAMETER_LIST })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "field")
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY_ACCESSOR })
    }

    should("never force a blank line before an annotated property accessor following another accessor") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAR, "var")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "value")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "0")
        builder.exitNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        propertyAccessor(builder, ctx, WNodeType.KW_GET, "get", annotated = false)
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        propertyAccessor(builder, ctx, WNodeType.KW_SET, "set", annotated = true)
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "var value = 0\n    get() = field\n    @Ann\n    set() = field"
    }

    should("never force a blank line before an annotated property accessor that is itself the first one") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAR, "var")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "value")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "0")
        builder.exitNode(ctx.apply { type = WNodeType.INTEGER_CONSTANT })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        propertyAccessor(builder, ctx, WNodeType.KW_GET, "get", annotated = true)
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "var value = 0\n    @Ann\n    get() = field"
    }

    should("indent a property's own PROPERTY_ACCESSOR one level deeper than the property itself") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n    ")
        propertyAccessor(builder, ctx, WNodeType.KW_GET, "get", annotated = false)
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val foo\n    get() = field"
    }

    should("leave a same-line PROPERTY_ACCESSOR untouched — indenting a gap with no newline is a no-op") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        propertyAccessor(builder, ctx, WNodeType.KW_GET, "get", annotated = false)
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val foo get() = field"
    }

    should(
        "strip the space before a PROPERTY_ACCESSOR's own parameter list even when its LPAR/RPAR are bare accessor children, not wrapped in VALUE_PARAMETER_LIST",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY_ACCESSOR })
        leaf(builder, ctx, WNodeType.KW_GET, "get")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "field")
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY_ACCESSOR })
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val foo get() = field"
    }

    should("collapse an existing multi-blank-line gap to exactly one blank line even where insertion also applies") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        bareClass(builder, ctx, "Foo")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "\n\n\n\n")
        bareClass(builder, ctx, "Bar")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "class Foo\n\nclass Bar"
    }

    should("insert a space after // when a line comment has none") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "//comment")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "// comment"
    }

    should("leave an already-spaced line comment untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// comment")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "// comment"
    }

    should("leave a bare // line comment untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "//")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "//"
    }

    should("leave //region, //endregion, //noinspection and //language= line comments untouched") {
        listOf("//region Foo", "//endregion", "//noinspection Foo", "//language=SQL").forEach { text ->
            val builder = DocBuilder(formatConfig())
            val ctx = WContext(filePath = "test.kt")
            builder.enterNode(ctx.apply { type = WNodeType.FILE })
            leaf(builder, ctx, WNodeType.EOL_COMMENT, text)
            builder.exitNode(ctx.apply { type = WNodeType.FILE })

            render(builder, ctx) shouldBe text
        }
    }

    should("leave block comments and KDoc without a leading space untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        leaf(builder, ctx, WNodeType.BLOCK_COMMENT, "/*no space*/")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.KDOC, "/**no space*/")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "/*no space*/ /**no space*/"
    }

    should("insert exactly one space before a trailing line comment directly touching code") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "1")
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "//trailing")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val x = 1 // trailing"
    }

    should("preserve an existing multi-space gap before a trailing line comment") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.KW_VAL, "val")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.IDENTIFIER, "x")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.EQ, "=")
        leaf(builder, ctx, WNodeType.WHITE_SPACE, " ")
        leaf(builder, ctx, WNodeType.INTEGER_LITERAL, "1")
        builder.exitNode(ctx.apply { type = WNodeType.PROPERTY })
        leaf(builder, ctx, WNodeType.WHITE_SPACE, "   ")
        leaf(builder, ctx, WNodeType.EOL_COMMENT, "// trailing")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "val x = 1   // trailing"
    }

    fun multilineRawStringTemplate(
        builder: DocBuilder,
        ctx: WContext,
        lines: List<String>,
        closingIndent: String?,
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        lines.forEach { line ->
            leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, line)
            leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        }
        if (closingIndent != null) {
            leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, closingIndent)
        }
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
    }

    fun trimIndentCall(
        builder: DocBuilder,
        ctx: WContext,
        methodName: String = "trimIndent",
    ) {
        leaf(builder, ctx, WNodeType.DOT, ".")
        builder.enterNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, methodName)
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        leaf(builder, ctx, WNodeType.LPAR, "(")
        leaf(builder, ctx, WNodeType.RPAR, ")")
        builder.exitNode(ctx.apply { type = WNodeType.VALUE_ARGUMENT_LIST })
        builder.exitNode(ctx.apply { type = WNodeType.CALL_EXPRESSION })
    }

    fun trimIndentReceiver(
        builder: DocBuilder,
        ctx: WContext,
        lines: List<String>,
        closingIndent: String?,
        methodName: String = "trimIndent",
    ) {
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        multilineRawStringTemplate(builder, ctx, lines, closingIndent)
        trimIndentCall(builder, ctx, methodName)
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
    }

    should("reindent a well-formed trimIndent() raw string's content and closing quote to one ambient indent level") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        trimIndentReceiver(builder, ctx, listOf("  line one", "  line two"), "  ")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n    line one\n    line two\n    \"\"\".trimIndent()"
    }

    should("leave a trimIndent() raw string containing a whitespace-only interior line untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        trimIndentReceiver(builder, ctx, listOf("  line one", "      ", "  line two"), "  ")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n  line one\n      \n  line two\n  \"\"\".trimIndent()"
    }

    should(
        "reindent a trimIndent() raw string across a genuinely blank interior line without planting indentation on it",
    ) {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  line one")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  line two")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  ")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        trimIndentCall(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n    line one\n\n\n    line two\n    \"\"\".trimIndent()"
    }

    should("leave a standalone multiline raw string with no trimIndent() call untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        multilineRawStringTemplate(builder, ctx, listOf("  line one", "  line two"), "  ")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n  line one\n  line two\n  \"\"\""
    }

    should("leave a raw string containing interpolation untouched even when followed by trimIndent()") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  hello ")
        builder.enterNode(ctx.apply { type = WNodeType.SHORT_STRING_TEMPLATE_ENTRY })
        leaf(builder, ctx, WNodeType.SHORT_TEMPLATE_ENTRY_START, "$")
        builder.enterNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        leaf(builder, ctx, WNodeType.IDENTIFIER, "name")
        builder.exitNode(ctx.apply { type = WNodeType.REFERENCE_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.SHORT_STRING_TEMPLATE_ENTRY })
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  ")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        trimIndentCall(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n  hello \$name\n  \"\"\".trimIndent()"
    }

    should("leave a trimMargin() raw string untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        trimIndentReceiver(builder, ctx, listOf("  |line one", "  |line two"), "  ", methodName = "trimMargin")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n  |line one\n  |line two\n  \"\"\".trimMargin()"
    }

    should("leave a trimIndent() raw string whose content touches the opening quotes untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "line one")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  ")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        trimIndentCall(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"line one\n  \"\"\".trimIndent()"
    }

    should("leave a trimIndent() raw string whose content touches the closing quotes untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.enterNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        leaf(builder, ctx, WNodeType.OPEN_QUOTE, "\"\"\"")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "\n")
        leaf(builder, ctx, WNodeType.LITERAL_STRING_TEMPLATE_ENTRY, "  line one")
        leaf(builder, ctx, WNodeType.CLOSING_QUOTE, "\"\"\"")
        builder.exitNode(ctx.apply { type = WNodeType.STRING_TEMPLATE })
        trimIndentCall(builder, ctx)
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n  line one\"\"\".trimIndent()"
    }

    should("leave a trimIndent() raw string with zero common indent untouched") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        trimIndentReceiver(builder, ctx, listOf("line one", "  line two"), "  ")
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\nline one\n  line two\n  \"\"\".trimIndent()"
    }

    should("compose reindentation with a further chain link after trimIndent()") {
        val builder = DocBuilder(formatConfig())
        val ctx = WContext(filePath = "test.kt")
        builder.enterNode(ctx.apply { type = WNodeType.FILE })
        builder.enterNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        trimIndentReceiver(builder, ctx, listOf("  line one"), "  ")
        trimIndentCall(builder, ctx, "uppercase")
        builder.exitNode(ctx.apply { type = WNodeType.DOT_QUALIFIED_EXPRESSION })
        builder.exitNode(ctx.apply { type = WNodeType.FILE })

        render(builder, ctx) shouldBe "\"\"\"\n    line one\n    \"\"\".trimIndent()\n    .uppercase()"
    }
})

private const val LIST_END = 100

private const val CALLEE_START = 50

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
        reports.add(
            ViolationReport(
                message = message,
                startOffset = startOffset,
                endOffset = endOffset,
                level = rule.config.effectiveLevel,
            ),
        )
        lastEdit = edits.firstOrNull()
    }
}
