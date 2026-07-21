package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NoUnitReturnDeletionSpanSpec :
    BaseSpec(
        {

            should("delete the colon, the single space, and Unit, leaving the space before the brace untouched") {
                val source = "fun foo(): Unit {}"
                val colonStart = source.indexOf(':')
                val typeReferenceEnd = source.indexOf(" {}")

                val edit = NoUnitReturnDeletionSpan.compute(colonStart, typeReferenceEnd)

                edit.startOffset shouldBe colonStart
                edit.endOffset shouldBe typeReferenceEnd
                edit.replacement shouldBe ""
                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "fun foo() {}"
            }

            should("collapse Unit directly adjacent to the colon with no space on either side") {
                val source = "fun foo():Unit{}"
                val colonStart = source.indexOf(':')
                val typeReferenceEnd = source.indexOf("{}")

                val edit = NoUnitReturnDeletionSpan.compute(colonStart, typeReferenceEnd)

                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "fun foo(){}"
            }

            should("leave a blank line before the opening brace untouched") {
                val source = "fun foo(): Unit\n\n{\n}"
                val colonStart = source.indexOf(':')
                val typeReferenceEnd = source.indexOf("\n\n{\n}")

                val edit = NoUnitReturnDeletionSpan.compute(colonStart, typeReferenceEnd)

                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "fun foo()\n\n{\n}"
            }

            should("leave a trailing block comment between Unit and the brace untouched") {
                val source = "fun foo(): Unit /* test */ {}"
                val colonStart = source.indexOf(':')
                val typeReferenceEnd = source.indexOf(" /* test */ {}")

                val edit = NoUnitReturnDeletionSpan.compute(colonStart, typeReferenceEnd)

                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "fun foo() /* test */ {}"
            }
        },
    )
