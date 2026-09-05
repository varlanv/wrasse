package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ClassMetricsFrameSpec : BaseSpec({

    should("start at zero for a fresh frame") {
        val frame = ClassMetricsFrame(-1, -1, "", "Class")

        frame.functionCount shouldBe 0
        frame.distinctCodeLines shouldBe 0
    }

    should("count each recorded function") {
        val frame = ClassMetricsFrame(-1, -1, "", "Class")

        frame.recordFunction()
        frame.recordFunction()
        frame.recordFunction()

        frame.functionCount shouldBe 3
    }

    should("count a repeated code line only once") {
        val frame = ClassMetricsFrame(-1, -1, "", "Class")

        frame.recordCodeLine(1)
        frame.recordCodeLine(1)
        frame.recordCodeLine(2)

        frame.distinctCodeLines shouldBe 2
    }

    should("carry the mutable name, kind label, and offsets") {
        val frame = ClassMetricsFrame(3, 6, "Foo", "Interface")

        frame.nameStart shouldBe 3
        frame.nameEnd shouldBe 6
        frame.declarationName shouldBe "Foo"
        frame.kindLabel shouldBe "Interface"
    }
})
