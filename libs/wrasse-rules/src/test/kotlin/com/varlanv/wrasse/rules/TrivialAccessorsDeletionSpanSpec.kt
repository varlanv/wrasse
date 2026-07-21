package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class TrivialAccessorsDeletionSpanSpec :
    BaseSpec(
        {

            should("delete the single space between the initializer and the accessor") {
                val source = "val prop: Int = 0 get() = field"
                val accessorStart = source.indexOf("get()")
                val accessorEnd = source.length

                val edit = TrivialAccessorsDeletionSpan.compute(source, accessorStart, accessorEnd)

                edit.startOffset shouldBe source.indexOf(" get()")
                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "val prop: Int = 0"
            }

            should("collapse a multiline gap back to the initializer") {
                val source = "val prop: Int = 0\nget() = field"
                val accessorStart = source.indexOf("get()")
                val accessorEnd = source.length

                val edit = TrivialAccessorsDeletionSpan.compute(source, accessorStart, accessorEnd)

                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "val prop: Int = 0"
            }

            should("stop trimming at a comment rather than deleting past it") {
                val source = "val prop: Int = 0 /* keep */ get() = field"
                val accessorStart = source.indexOf("get()")
                val accessorEnd = source.length

                val edit = TrivialAccessorsDeletionSpan.compute(source, accessorStart, accessorEnd)

                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "val prop: Int = 0 /* keep */"
            }
        },
    )
