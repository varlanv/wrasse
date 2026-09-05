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
