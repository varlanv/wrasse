package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallArgument
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NamedArgumentsDecisionSpec : BaseSpec({

    fun argument(
        start: Int,
        end: Int,
        name: String,
        vararg: Boolean = false,
        index: Int = 0,
        named: Boolean = false,
    ) = WCallArgument(start, end, name, vararg, index, named)

    fun site(
        callStart: Int,
        callEnd: Int,
        listStart: Int,
        pkg: String = "sample",
        cls: String? = null,
        name: String = "f",
        stable: Boolean = true,
        arguments: List<WCallArgument> = listOf(argument(listStart + 1, callEnd - 1, "a")),
        parameterCount: Int = 2,
    ) = WCallSite(callStart, callEnd, pkg, cls, name, stable, arguments, parameterCount = parameterCount)

    should("put a call and its nested call arguments in scope, transitively, but not an unrelated flat call") {
        val inner = site(callStart = 10, callEnd = 18, listStart = 15)
        val middle = site(callStart = 5, callEnd = 19, listStart = 8, arguments = listOf(argument(10, 18, "m")))
        val outer = site(callStart = 0, callEnd = 20, listStart = 3, arguments = listOf(argument(5, 19, "o")))
        val flat = site(callStart = 30, callEnd = 40, listStart = 33)
        NamedArgumentsDecision.callsInScope(
            listOf(flat, outer, inner, middle),
            allCalls = false,
            threshold = 1,
        ) shouldBe setOf(20, 19, 18)
    }

    should("put every call in scope with all-calls") {
        val flat = site(callStart = 30, callEnd = 40, listStart = 33)
        NamedArgumentsDecision.callsInScope(listOf(flat), allCalls = true, threshold = 1) shouldBe setOf(40)
    }

    should("leave a callee below the threshold out of scope while still reaching the calls nested in it") {
        val inner = site(callStart = 10, callEnd = 18, listStart = 15, parameterCount = 2)
        val middle = site(
            callStart = 5,
            callEnd = 19,
            listStart = 8,
            arguments = listOf(argument(10, 18, "m")),
            parameterCount = 1,
        )
        val outer = site(
            callStart = 0,
            callEnd = 20,
            listStart = 3,
            arguments = listOf(argument(5, 19, "o")),
            parameterCount = 3,
        )
        NamedArgumentsDecision.callsInScope(
            listOf(outer, inner, middle),
            allCalls = false,
            threshold = 2,
        ) shouldBe setOf(20, 18)
        NamedArgumentsDecision.callsInScope(
            listOf(outer, inner, middle),
            allCalls = false,
            threshold = 3,
        ) shouldBe setOf(20)
        NamedArgumentsDecision.callsInScope(
            listOf(outer, inner, middle),
            allCalls = true,
            threshold = 2,
        ) shouldBe setOf(20, 18)
    }

    should("drop the names of an all-named call to a narrow callee only when positional form means the same call") {
        fun named(
            start: Int,
            end: Int,
            nameEnd: Int,
        ) = WrittenArgument(start, end, nameEnd)

        fun positional(start: Int, end: Int) = WrittenArgument(start, end, null)
        val inOrder = site(
            0,
            30,
            1,
            arguments = listOf(argument(6, 7, "a", index = 0), argument(14, 15, "b", index = 1)),
        )
        NamedArgumentsDecision.positionalEdits(inOrder, listOf(named(2, 7, 6), named(10, 15, 14))).map {
            Triple(it.startOffset, it.endOffset, it.replacement)
        } shouldBe listOf(Triple(2, 6, ""), Triple(10, 14, ""))

        val reordered = site(
            0,
            30,
            1,
            arguments = listOf(argument(6, 7, "b", index = 1), argument(14, 15, "a", index = 0)),
        )
        NamedArgumentsDecision.positionalEdits(
            reordered,
            listOf(named(2, 7, 6), named(10, 15, 14)),
        ) shouldBe emptyList()

        val skipsFirst = site(0, 30, 1, arguments = listOf(argument(6, 7, "b", index = 1)))
        NamedArgumentsDecision.positionalEdits(skipsFirst, listOf(named(2, 7, 6))) shouldBe emptyList()

        val pastDefaulted = site(
            0,
            40,
            1,
            arguments = listOf(argument(9, 12, "text", index = 0), argument(27, 32, "ignoreCase", index = 2)),
            parameterCount = 3,
        )
        NamedArgumentsDecision.positionalEdits(
            pastDefaulted,
            listOf(named(2, 12, 9), named(14, 32, 27)),
        ) shouldBe emptyList()

        val vararg = site(0, 30, 1, arguments = listOf(argument(7, 12, "xs", vararg = true, index = 0)))
        NamedArgumentsDecision.positionalEdits(vararg, listOf(named(2, 12, 7))) shouldBe emptyList()

        val mixed = site(0, 30, 1, arguments = listOf(argument(2, 3, "a", index = 0), argument(10, 11, "b", index = 1)))
        NamedArgumentsDecision
            .positionalEdits(mixed, listOf(positional(2, 3), named(6, 11, 10)))
            .map { Triple(it.startOffset, it.endOffset, it.replacement) } shouldBe listOf(Triple(6, 10, ""))

        val allPositional = site(0, 30, 1, arguments = listOf(argument(2, 3, "a", index = 0)))
        NamedArgumentsDecision.positionalEdits(allPositional, listOf(positional(2, 3))) shouldBe emptyList()
    }

    should("not count a trailing lambda's parameter toward the threshold") {
        val withLambda = site(
            0,
            30,
            3,
            arguments = listOf(argument(4, 5, "e", index = 0), argument(7, 30, "message", index = 1)),
            parameterCount = 2,
        )
        NamedArgumentsDecision.parenthesizedParameterCount(withLambda) shouldBe 1
        val parenthesized = site(
            0,
            30,
            3,
            arguments = listOf(argument(4, 5, "e", index = 0), argument(7, 29, "message", index = 1)),
            parameterCount = 2,
        )
        NamedArgumentsDecision.parenthesizedParameterCount(parenthesized) shouldBe 2
        NamedArgumentsDecision.parenthesizedParameterCount(
            site(0, 5, 3, arguments = emptyList(), parameterCount = 2),
        ) shouldBe 2
    }

    should("recognize plain and backticked named arguments") {
        fun named(text: String) = NamedArgumentsDecision.isNamedArgument(text, 0, text.length)
        named("x = 1") shouldBe true
        named("x=1") shouldBe true
        named("  `weird name` = f()") shouldBe true
        named("x == 1") shouldBe false
        named("1") shouldBe false
        named("foo(a = 1)") shouldBe false
        named("{ a -> a }") shouldBe false
        named("`unterminated") shouldBe false
    }

    should("find the value start right after the name = prefix regardless of what the value itself starts with") {
        fun valueStart(text: String) = NamedArgumentsDecision.namedArgumentValueStart(text, 0, text.length)
        valueStart("and = (x)") shouldBe "and = (x)".indexOf('(')
        valueStart("delta = (y) + 1") shouldBe "delta = (y) + 1".indexOf('(')
        valueStart("block = { 1 }") shouldBe "block = { 1 }".indexOf('{')
        valueStart("text = \"hello\"") shouldBe "text = \"hello\"".indexOf('"')
        valueStart("value = @Suppress(\"X\") 5") shouldBe "value = @Suppress(\"X\") 5".indexOf('@')
        valueStart("value = label@ 5") shouldBe "value = label@ 5".indexOf("label")
    }

    should("drop only the name = prefix of a parenthesized argument, never its opening paren") {
        val text = "whenMatchedDelete(and = (1 == 1))"
        val argStart = text.indexOf("and")
        val valueStart = text.indexOf("(1 == 1)")
        val valueInnerStart = valueStart + 1
        val valueInnerEnd = text.indexOf(")", valueInnerStart)
        val argEnd = valueInnerEnd + 1
        val mapped = argument(valueInnerStart, valueInnerEnd, "and", index = 0)
        val s = site(0, text.length, text.indexOf("("), arguments = listOf(mapped))
        val nameEnd = NamedArgumentsDecision.namedArgumentValueStart(text, argStart, argEnd)
        nameEnd shouldBe valueStart
        NamedArgumentsDecision
            .positionalEdits(s, listOf(WrittenArgument(argStart, argEnd, nameEnd)))
            .map { Triple(it.startOffset, it.endOffset, it.replacement) } shouldBe
            listOf(Triple(argStart, valueStart, ""))
    }

    should("call a list mixed only when a named argument meets a positional one outside a vararg") {
        val plain = site(0, 30, 1, arguments = listOf(argument(2, 3, "a", index = 0), argument(10, 11, "b", index = 1)))
        NamedArgumentsDecision.isMixed(
            plain,
            listOf(WrittenArgument(2, 3, null), WrittenArgument(6, 11, 10)),
        ) shouldBe true
        NamedArgumentsDecision.isMixed(
            plain,
            listOf(WrittenArgument(2, 3, null), WrittenArgument(10, 11, null)),
        ) shouldBe false
        val withVararg = site(
            0,
            30,
            1,
            arguments = listOf(argument(6, 7, "a", index = 0), argument(10, 11, "xs", vararg = true, index = 1)),
        )
        NamedArgumentsDecision.isMixed(
            withVararg,
            listOf(WrittenArgument(2, 7, 6), WrittenArgument(10, 11, null)),
        ) shouldBe false
    }

    should("exclude java and javax callees by package prefix, whole segments only") {
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "java.util"), listOf("java", "javax")) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(
            site(0, 5, 1, pkg = "javax.swing"),
            listOf("java", "javax"),
        ) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "java"), listOf("java", "javax")) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(
            site(0, 5, 1, pkg = "javaland.util"),
            listOf("java", "javax"),
        ) shouldBe false
        NamedArgumentsDecision.isExcludedCallee(
            site(0, 5, 1, pkg = "kotlin.collections"),
            listOf("java", "javax"),
        ) shouldBe false
    }

    should("exclude a callee without stable parameter names and a function type's invoke") {
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, stable = false), emptyList()) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(
            site(0, 5, 1, pkg = "kotlin", cls = "kotlin.Function2", name = "invoke"),
            emptyList(),
        ) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(
            site(0, 5, 1, pkg = "kotlin", cls = "kotlin.Pair", name = "invoke"),
            emptyList(),
        ) shouldBe false
    }

    should("skip comments, alongside whitespace, around the name and around the = when scanning for a named argument") {
        fun valueStart(text: String) = NamedArgumentsDecision.namedArgumentValueStart(text, 0, text.length)
        valueStart("a /* note */ = 1") shouldBe "a /* note */ = 1".indexOf('1')
        valueStart("/* c */ a = 1") shouldBe "/* c */ a = 1".indexOf('1')
        valueStart("a = /* note */ 1") shouldBe "a = /* note */ 1".indexOf('1')
        valueStart("a // note\n = 1") shouldBe "a // note\n = 1".indexOf('1')
        valueStart("// c\n a = 1") shouldBe "// c\n a = 1".indexOf('1')
        valueStart("a /* unterminated") shouldBe null
    }

    should("treat FIR's own naming verdict as authoritative and flag any written argument that disagrees") {
        val s = site(
            callStart = 0,
            callEnd = 20,
            listStart = 1,
            arguments = listOf(argument(2, 5, "a", index = 0, named = true)),
        )
        NamedArgumentsDecision.hasSyntaxMismatch(s, listOf(WrittenArgument(2, 5, null))) shouldBe true
        NamedArgumentsDecision.hasSyntaxMismatch(s, listOf(WrittenArgument(2, 5, 2))) shouldBe false

        val positional = site(
            callStart = 0,
            callEnd = 20,
            listStart = 1,
            arguments = listOf(argument(2, 5, "a", index = 0, named = false)),
        )
        NamedArgumentsDecision.hasSyntaxMismatch(positional, listOf(WrittenArgument(2, 5, 2))) shouldBe true
        NamedArgumentsDecision.hasSyntaxMismatch(positional, listOf(WrittenArgument(2, 5, null))) shouldBe false
    }

    should("insert names before written arguments that are positional and map to a non-vararg parameter") {
        val s = site(
            callStart = 0,
            callEnd = 30,
            listStart = 3,
            arguments = listOf(
                argument(4, 6, "a"),
                argument(12, 13, "b"),
                argument(15, 16, "v", vararg = true),
                argument(20, 29, "block"),
            ),
        )
        val written = listOf(WrittenArgument(4, 6, null), WrittenArgument(8, 13, 8), WrittenArgument(15, 16, null))
        val edits = NamedArgumentsDecision.nameEdits(s, written)
        edits.map { Triple(it.startOffset, it.endOffset, it.replacement) } shouldBe listOf(Triple(4, 4, "a = "))
    }
})

// fixture-option: trailing-newline
// fixture-aux-file: aux/Stubs.kt
// fixture-aux-file: aux/Kotest.kt
// fixture-aux-file: aux/KotestCollections.kt
// fixture-aux-file: aux/KotestNulls.kt
// fixture-aux-file: aux/KotestTypes.kt
// fixture-aux-file: aux/KotestAssertions.kt
// fixture-aux-file: aux/KotestThrowables.kt
// fixture-aux-file: aux/lang/ConfigValueJsonc.kt
// fixture-aux-file: aux/lang/FileEdits.kt
// fixture-aux-file: aux/lang/FileWalkUp.kt
// fixture-aux-file: aux/lang/FormatRequest.kt
// fixture-aux-file: aux/lang/HexEncoding.kt
// fixture-aux-file: aux/lang/PerfRecorder.kt
// fixture-aux-file: aux/lang/SafeProperties.kt
// fixture-aux-file: aux/lang/Sha256.kt
// fixture-aux-file: aux/lang/StringSlice.kt
// fixture-aux-file: aux/lang/WEdit.kt
// fixture-aux-file: aux/lang/WPatchApplier.kt
// fixture-aux-file: aux/lang/WPatchReader.kt
// fixture-aux-file: aux/lang/WPatchStore.kt
// fixture-aux-file: aux/lang/WPatchWriter.kt
// fixture-aux-file: aux/lang/WPerf.kt
// fixture-aux-file: aux/lang/WReport.kt
// fixture-aux-file: aux/lang/WReportReplay.kt
// fixture-aux-file: aux/lang/WReportStore.kt
// fixture-aux-file: aux/model/WCallSite.kt
// fixture-aux-file: aux/rules/NamedArgumentsDecision.kt
// expect-clean
