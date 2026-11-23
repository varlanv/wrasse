package com.varlanv.wrasse.model

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class FormatRegionsSpec : BaseSpec({

    fun spans(regions: FormatRegions): List<Pair<Int, Int>> = regions.all().map { it.start to it.end }

    should("parse exactly the two directive texts") {
        FormatDirective.parse("// wrasse fmt:off") shouldBe FormatDirective.OFF
        FormatDirective.parse("// wrasse fmt:on") shouldBe FormatDirective.ON
    }

    should("reject every near-miss directive text") {
        val nearMisses = listOf(
            "//wrasse fmt:off",
            "// wrasse  fmt:off",
            "// wrasse fmt:off ",
            " // wrasse fmt:off",
            "// wrasse\tfmt:off",
            "// WRASSE FMT:OFF",
            "// wrasse fmt:off because",
            "// wrasse fmt:onn",
            "/* wrasse fmt:off */",
            "",
        )
        nearMisses.map { FormatDirective.parse(it) } shouldBe nearMisses.map { null }
    }

    should("close a region from the off line start to the on comment end") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 40, 56)

        spans(regions) shouldBe listOf(10 to 56)
    }

    should("leave a region never closed open-ended") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)

        spans(regions) shouldBe listOf(10 to FormatRegions.OPEN_END)
    }

    should("ignore an off inside an open region and an on with none open") {
        val regions = FormatRegions()
        regions.add(FormatDirective.ON, 0, 16)
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.OFF, 30, 47)
        regions.add(FormatDirective.ON, 50, 66)
        regions.add(FormatDirective.ON, 70, 86)

        spans(regions) shouldBe listOf(10 to 66)
    }

    should("collect several regions in source order") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 30, 46)
        regions.add(FormatDirective.OFF, 50, 67)
        regions.add(FormatDirective.ON, 70, 86)

        spans(regions) shouldBe listOf(10 to 46, 50 to 86)
    }

    should("report no regions as empty") {
        FormatRegions().isEmpty shouldBe true
        FormatRegions().intersects(0, 100) shouldBe false
    }

    should("intersect an edit sharing at least one offset with a region") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 30, 46)

        regions.intersects(0, 10) shouldBe false
        regions.intersects(0, 11) shouldBe true
        regions.intersects(20, 25) shouldBe true
        regions.intersects(45, 60) shouldBe true
        regions.intersects(46, 60) shouldBe false
        regions.intersects(0, 100) shouldBe true
    }

    should("intersect a zero-width insertion only strictly inside a region or at its start") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 30, 46)

        regions.intersects(9, 9) shouldBe false
        regions.intersects(10, 10) shouldBe true
        regions.intersects(45, 45) shouldBe true
        regions.intersects(46, 46) shouldBe false
    }

    should("intersect an insertion at the very end of an open-ended region") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)

        regions.intersects(500, 500) shouldBe true
    }

    should("contain an offset from a region start up to but excluding its end") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 30, 46)

        regions.contains(9) shouldBe false
        regions.contains(10) shouldBe true
        regions.contains(45) shouldBe true
        regions.contains(46) shouldBe false
    }

    should("contain every offset after an off that is never closed") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)

        regions.contains(9) shouldBe false
        regions.contains(10_000) shouldBe true
    }

    should("add a span that touches no region") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 30, 46)
        regions.addSpan(50, 60)

        spans(regions) shouldBe listOf(10 to 46, 50 to 60)
        regions.contains(55) shouldBe true
    }

    should("skip a span touching an existing region, open-ended or closed") {
        val regions = FormatRegions()
        regions.add(FormatDirective.OFF, 10, 27)
        regions.add(FormatDirective.ON, 30, 46)
        regions.add(FormatDirective.OFF, 100, 117)
        regions.addSpan(40, 50)
        regions.addSpan(90, 120)
        regions.addSpan(200, 210)

        spans(regions) shouldBe listOf(10 to 46, 100 to FormatRegions.OPEN_END)
    }
})
