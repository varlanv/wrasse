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
    ) = WCallArgument(start, end, name, vararg, index)

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
        fun written(
            vararg spans: Pair<Int, Int>,
            named: Boolean = true,
        ) = spans.map { (start, end) -> WrittenArgument(start, end, named) }
        val inOrder = site(
            0,
            30,
            1,
            arguments = listOf(argument(6, 7, "a", index = 0), argument(14, 15, "b", index = 1)),
        )
        NamedArgumentsDecision.positionalEdits(inOrder, written(2 to 7, 10 to 15)).map {
            Triple(it.startOffset, it.endOffset, it.replacement)
        } shouldBe listOf(Triple(2, 6, ""), Triple(10, 14, ""))

        val reordered = site(
            0,
            30,
            1,
            arguments = listOf(argument(6, 7, "b", index = 1), argument(14, 15, "a", index = 0)),
        )
        NamedArgumentsDecision.positionalEdits(reordered, written(2 to 7, 10 to 15)) shouldBe emptyList()

        val skipsFirst = site(0, 30, 1, arguments = listOf(argument(6, 7, "b", index = 1)))
        NamedArgumentsDecision.positionalEdits(skipsFirst, written(2 to 7)) shouldBe emptyList()

        val vararg = site(0, 30, 1, arguments = listOf(argument(7, 12, "xs", vararg = true, index = 0)))
        NamedArgumentsDecision.positionalEdits(vararg, written(2 to 12)) shouldBe emptyList()

        val mixed = site(0, 30, 1, arguments = listOf(argument(2, 3, "a", index = 0), argument(10, 11, "b", index = 1)))
        NamedArgumentsDecision
            .positionalEdits(mixed, listOf(WrittenArgument(2, 3, false), WrittenArgument(6, 11, true)))
            .map { Triple(it.startOffset, it.endOffset, it.replacement) } shouldBe listOf(Triple(6, 10, ""))

        val allPositional = site(0, 30, 1, arguments = listOf(argument(2, 3, "a", index = 0)))
        NamedArgumentsDecision.positionalEdits(allPositional, written(2 to 3, named = false)) shouldBe emptyList()
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

    should("call a list mixed only when a named argument meets a positional one outside a vararg") {
        val plain = site(0, 30, 1, arguments = listOf(argument(2, 3, "a", index = 0), argument(10, 11, "b", index = 1)))
        NamedArgumentsDecision.isMixed(
            plain,
            listOf(WrittenArgument(2, 3, false), WrittenArgument(6, 11, true)),
        ) shouldBe true
        NamedArgumentsDecision.isMixed(
            plain,
            listOf(WrittenArgument(2, 3, false), WrittenArgument(10, 11, false)),
        ) shouldBe false
        val withVararg = site(
            0,
            30,
            1,
            arguments = listOf(argument(6, 7, "a", index = 0), argument(10, 11, "xs", vararg = true, index = 1)),
        )
        NamedArgumentsDecision.isMixed(
            withVararg,
            listOf(WrittenArgument(2, 7, true), WrittenArgument(10, 11, false)),
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
        val written = listOf(WrittenArgument(4, 6, false), WrittenArgument(8, 13, true), WrittenArgument(15, 16, false))
        val edits = NamedArgumentsDecision.nameEdits(s, written)
        edits.map { Triple(it.startOffset, it.endOffset, it.replacement) } shouldBe listOf(Triple(4, 4, "a = "))
    }
})
