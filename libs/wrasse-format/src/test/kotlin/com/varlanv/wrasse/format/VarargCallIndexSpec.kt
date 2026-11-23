package com.varlanv.wrasse.format

import com.varlanv.wrasse.model.WCallArgument
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class VarargCallIndexSpec : BaseSpec({

    fun argument(
        start: Int,
        end: Int,
        isVararg: Boolean = true,
        isNamed: Boolean = false,
    ) = WCallArgument(start, end, "xs", isVararg, isNamed = isNamed)

    fun site(
        callEnd: Int = 30,
        parameterCount: Int = 1,
        hasOnlyVarargParameter: Boolean = parameterCount == 1,
        arguments: List<WCallArgument> = listOf(argument(5, 6), argument(8, 9), argument(11, 12)),
    ) = WCallSite(
        callStartOffset = 0,
        callEndOffset = callEnd,
        calleePackageFqName = "sample",
        calleeClassFqName = null,
        calleeName = "f",
        hasStableParameterNames = true,
        arguments = arguments,
        parameterCount = parameterCount,
        hasOnlyVarargParameter = hasOnlyVarargParameter,
    )

    should("match the argument list of a call whose only parameter is a vararg") {
        VarargCallIndex.build(listOf(site())).matches(4, 30, 3) shouldBe true
    }

    should("match nothing without call sites") {
        VarargCallIndex.build(emptyList()).matches(4, 30, 3) shouldBe false
    }

    should("not match a callee that declares another parameter") {
        VarargCallIndex.build(listOf(site(parameterCount = 2))).matches(4, 30, 3) shouldBe false
    }

    should("not match a call with fewer than two vararg elements") {
        val single = site(arguments = listOf(argument(5, 6)))
        VarargCallIndex.build(listOf(single)).matches(4, 30, 1) shouldBe false
    }

    should("not match a call passing a non-vararg argument") {
        val mixed = site(arguments = listOf(argument(5, 6, isVararg = false), argument(8, 9)))
        VarargCallIndex.build(listOf(mixed)).matches(4, 30, 2) shouldBe false
    }

    should("not match a call passing a named argument") {
        val named = site(arguments = listOf(argument(5, 6, isNamed = true), argument(8, 9)))
        VarargCallIndex.build(listOf(named)).matches(4, 30, 2) shouldBe false
    }

    should("not match an argument list that does not end where the call ends") {
        VarargCallIndex.build(listOf(site(callEnd = 40))).matches(4, 30, 3) shouldBe false
    }

    should("not match when FIR mapped a different number of arguments than the list holds") {
        VarargCallIndex.build(listOf(site())).matches(4, 30, 4) shouldBe false
    }

    should("not match when a mapped argument lies outside the list") {
        val outside = site(arguments = listOf(argument(1, 2), argument(8, 9)))
        VarargCallIndex.build(listOf(outside)).matches(4, 30, 2) shouldBe false
    }

    should("leave out every call that shares its end offset with another resolved call") {
        val index = VarargCallIndex.build(listOf(site(), site(parameterCount = 2)))
        index.matches(4, 30, 3) shouldBe false
    }

    should("find the resolved call ending where the argument list ends, whatever it declares") {
        val other = site(parameterCount = 2)

        VarargCallIndex.build(listOf(other)).callEndingAt(30) shouldBe other
    }

    should("find the call of a callee declaring only a vararg although no argument was passed") {
        val empty = site(arguments = emptyList())

        VarargCallIndex.build(listOf(empty)).callEndingAt(30) shouldBe empty
        VarargCallIndex.build(listOf(empty)).matches(4, 30, 0) shouldBe false
    }

    should("find nothing ending at another offset, without call sites, or for a shared end offset") {
        VarargCallIndex.build(listOf(site())).callEndingAt(31) shouldBe null
        VarargCallIndex.build(emptyList()).callEndingAt(30) shouldBe null
        VarargCallIndex.build(listOf(site(), site(parameterCount = 2))).callEndingAt(30) shouldBe null
    }

    should("not match a callee whose lone parameter is not a vararg although every argument maps to it") {
        val index = VarargCallIndex.build(listOf(site(hasOnlyVarargParameter = false)))

        index.matches(4, 30, 3) shouldBe false
    }
})
