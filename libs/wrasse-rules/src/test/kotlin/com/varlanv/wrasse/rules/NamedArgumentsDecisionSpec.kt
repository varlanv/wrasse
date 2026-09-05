package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WCallArgument
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NamedArgumentsDecisionSpec : BaseSpec({

    fun argument(start: Int, end: Int, name: String, vararg: Boolean = false) = WCallArgument(start, end, name, vararg)

    fun site(
        callStart: Int,
        callEnd: Int,
        listStart: Int,
        pkg: String = "sample",
        cls: String? = null,
        name: String = "f",
        stable: Boolean = true,
        arguments: List<WCallArgument> = listOf(argument(listStart + 1, callEnd - 1, "a")),
    ) = WCallSite(callStart, callEnd, pkg, cls, name, stable, arguments)

    should("put a call and its nested call arguments in scope, transitively, but not an unrelated flat call") {
        val inner = site(callStart = 10, callEnd = 18, listStart = 15)
        val middle = site(callStart = 5, callEnd = 19, listStart = 8, arguments = listOf(argument(10, 18, "m")))
        val outer = site(callStart = 0, callEnd = 20, listStart = 3, arguments = listOf(argument(5, 19, "o")))
        val flat = site(callStart = 30, callEnd = 40, listStart = 33)
        NamedArgumentsDecision.callsInScope(listOf(flat, outer, inner, middle), allCalls = false) shouldBe setOf(20, 19, 18)
    }

    should("put every call in scope with all-calls") {
        val flat = site(callStart = 30, callEnd = 40, listStart = 33)
        NamedArgumentsDecision.callsInScope(listOf(flat), allCalls = true) shouldBe setOf(40)
    }

    should("exclude java and javax callees by package prefix, whole segments only") {
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "java.util"), listOf("java", "javax")) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "javax.swing"), listOf("java", "javax")) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "java"), listOf("java", "javax")) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "javaland.util"), listOf("java", "javax")) shouldBe false
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "kotlin.collections"), listOf("java", "javax")) shouldBe false
    }

    should("exclude a callee without stable parameter names and a function type's invoke") {
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, stable = false), emptyList()) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "kotlin", cls = "kotlin.Function2", name = "invoke"), emptyList()) shouldBe true
        NamedArgumentsDecision.isExcludedCallee(site(0, 5, 1, pkg = "kotlin", cls = "kotlin.Pair", name = "invoke"), emptyList()) shouldBe false
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
