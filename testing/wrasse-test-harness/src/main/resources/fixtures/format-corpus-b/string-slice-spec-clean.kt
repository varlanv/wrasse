package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class StringSliceSpec : BaseSpec({

    val source = "val name = \"x\"\n    return name\n"

    should("expose a window without copying and copy only on toString") {
        val slice = StringSlice(source, 4, 8)
        slice.length shouldBe 4
        slice[0] shouldBe 'n'
        slice.toString() shouldBe "name"
        val sub = slice.subSequence(1, 3)
        sub.source shouldBe source
        sub.start shouldBe 5
        sub.end shouldBe 7
        sub.toString() shouldBe "am"
    }

    should("compare by content when the slice is the receiver") {
        val slice = StringSlice(source, 4, 8)
        slice.equals("name") shouldBe true
        (slice == StringSlice("a name b", 2, 6)) shouldBe true
        slice.equals("names") shouldBe false
        slice.hashCode() shouldBe "name".hashCode()
    }

    should("find characters within the window only") {
        val line = StringSlice(source, 15, 31)
        line.toString() shouldBe "    return name\n"
        line.indexOf('\n') shouldBe 15
        line.lastIndexOf('n') shouldBe 11
        StringSlice(source, 4, 8).indexOf('\n') shouldBe -1
        line.indexOfChar('r', 5) shouldBe 8
        line.containsChar('=') shouldBe false
    }

    should("reject a window outside its source") {
        shouldThrow<IllegalArgumentException> { StringSlice("abc", 2, 5) }.message shouldBe "Slice 2..5 outside 0..3"
        shouldThrow<IndexOutOfBoundsException> { StringSlice("abc", 1, 2)[1] }.message shouldBe "Index 1, length 1"
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
// expect-clean
