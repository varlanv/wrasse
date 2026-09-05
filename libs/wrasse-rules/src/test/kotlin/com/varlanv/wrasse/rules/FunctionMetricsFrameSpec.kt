package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FunctionMetricsFrameSpec : BaseSpec({

    should("start with a baseline complexity of 1 and zero everything else") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.complexity shouldBe 1
        frame.returnCount shouldBe 0
        frame.throwCount shouldBe 0
        frame.maxNestingDepth shouldBe 0
        frame.distinctCodeLines shouldBe 0
    }

    should("count returns and throws independently") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.recordReturn()
        frame.recordReturn()
        frame.recordThrow()

        frame.returnCount shouldBe 2
        frame.throwCount shouldBe 1
    }

    should("accumulate complexity across additions") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.addComplexity(1)
        frame.addComplexity(1)
        frame.addComplexity(1)

        frame.complexity shouldBe 4
    }

    should("track the max nesting depth across sequential sibling constructs, not their sum") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.enterNestingConstruct()
        frame.exitNestingConstruct()
        frame.enterNestingConstruct()
        frame.exitNestingConstruct()

        frame.maxNestingDepth shouldBe 1
    }

    should("track the max nesting depth across truly nested constructs") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.enterNestingConstruct()
        frame.enterNestingConstruct()
        frame.enterNestingConstruct()
        frame.exitNestingConstruct()
        frame.exitNestingConstruct()
        frame.exitNestingConstruct()

        frame.maxNestingDepth shouldBe 3
    }

    should("keep the deepest point when a later construct nests less deeply") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        repeat(4) { frame.enterNestingConstruct() }
        repeat(4) { frame.exitNestingConstruct() }
        frame.enterNestingConstruct()
        frame.exitNestingConstruct()

        frame.maxNestingDepth shouldBe 4
    }

    should("remember the deepest point reached even after unwinding") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.enterNestingConstruct()
        frame.enterNestingConstruct()
        frame.enterNestingConstruct()
        frame.exitNestingConstruct()
        frame.exitNestingConstruct()
        frame.enterNestingConstruct()
        frame.enterNestingConstruct()
        frame.exitNestingConstruct()
        frame.exitNestingConstruct()
        frame.exitNestingConstruct()

        frame.maxNestingDepth shouldBe 3
    }

    should("count a repeated code line only once") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.recordCodeLine(5)
        frame.recordCodeLine(5)
        frame.recordCodeLine(5)

        frame.distinctCodeLines shouldBe 1
    }

    should("count each distinct code line once, in any order revisited") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.recordCodeLine(5)
        frame.recordCodeLine(6)
        frame.recordCodeLine(6)
        frame.recordCodeLine(7)

        frame.distinctCodeLines shouldBe 3
    }

    should("count a line revisited non-consecutively as a new line, matching a source-order walk") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.recordCodeLine(5)
        frame.recordCodeLine(6)
        frame.recordCodeLine(5)

        frame.distinctCodeLines shouldBe 3
    }

    should("carry the name and offsets given at construction") {
        val frame = FunctionMetricsFrame(10, 15, "foo")

        frame.nameStart shouldBe 10
        frame.nameEnd shouldBe 15
        frame.functionName shouldBe "foo"
    }

    should("allow the name and offsets to be set later, once discovered during the walk") {
        val frame = FunctionMetricsFrame(-1, -1, "")

        frame.nameStart = 20
        frame.nameEnd = 23
        frame.functionName = "bar"

        frame.nameStart shouldBe 20
        frame.nameEnd shouldBe 23
        frame.functionName shouldBe "bar"
    }
})
