package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UnnecessaryInheritanceDeletionSpanSpec : BaseSpec({

    should("delete the colon, the space, and the sole entry, leaving the body untouched") {
        val source = "class Foo : Any() {}"
        val entryStart = source.indexOf("Any()")
        val entryEnd = entryStart + "Any()".length

        val edit = UnnecessaryInheritanceDeletionSpan.computeSoleEntry(source, entryStart, entryEnd)

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo {}"
    }

    should("collapse a sole entry with no space around the colon") {
        val source = "class Foo:Any()"
        val entryStart = source.indexOf("Any()")
        val entryEnd = entryStart + "Any()".length

        val edit = UnnecessaryInheritanceDeletionSpan.computeSoleEntry(source, entryStart, entryEnd)

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo"
    }

    should("collapse a sole entry whose colon sits on its own line") {
        val source = "class Foo :\n    Any() {}"
        val entryStart = source.indexOf("Any()")
        val entryEnd = entryStart + "Any()".length

        val edit = UnnecessaryInheritanceDeletionSpan.computeSoleEntry(source, entryStart, entryEnd)

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo {}"
    }

    should("bail on a sole entry when a comment sits between the colon and the entry") {
        val source = "class Foo : /* c */ Any() {}"
        val entryStart = source.indexOf("Any()")
        val entryEnd = entryStart + "Any()".length

        val edit = UnnecessaryInheritanceDeletionSpan.computeSoleEntry(source, entryStart, entryEnd)

        edit shouldBe null
    }

    should("delete a leading entry plus its trailing comma and whitespace") {
        val source = "class Foo : Any(), Bar"
        val entryStart = source.indexOf("Any()")
        val nextEntryStart = source.indexOf("Bar")

        val edit = UnnecessaryInheritanceDeletionSpan.computeLeadingEntry(entryStart, nextEntryStart, hasAdjacentComment = false)

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo : Bar"
    }

    should("bail on a leading entry when a comment sits before the next entry") {
        val edit = UnnecessaryInheritanceDeletionSpan.computeLeadingEntry(entryStart = 12, nextEntryStart = 30, hasAdjacentComment = true)

        edit shouldBe null
    }

    should("delete a trailing entry plus its leading comma and whitespace") {
        val source = "class Foo : Bar, Any()"
        val previousEntryEnd = source.indexOf("Bar") + "Bar".length
        val entryEnd = source.length

        val edit = UnnecessaryInheritanceDeletionSpan.computeTrailingEntry(previousEntryEnd, entryEnd, hasAdjacentComment = false)

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo : Bar"
    }

    should("bail on a trailing entry when a comment sits after the previous entry") {
        val edit = UnnecessaryInheritanceDeletionSpan.computeTrailingEntry(previousEntryEnd = 15, entryEnd = 30, hasAdjacentComment = true)

        edit shouldBe null
    }
})
