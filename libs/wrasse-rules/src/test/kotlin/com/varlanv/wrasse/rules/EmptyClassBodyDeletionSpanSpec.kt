package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class EmptyClassBodyDeletionSpanSpec :
    BaseSpec(
        {

            should("delete the single space between the header and the braces") {
                val source = "class Foo {}"
                val bodyStart = source.indexOf('{')
                val bodyEnd = source.length

                val edit = EmptyClassBodyDeletionSpan.compute(source, bodyStart, bodyEnd)

                edit.startOffset shouldBe source.indexOf(" {}")
                edit.endOffset shouldBe bodyEnd
                edit.replacement shouldBe ""
                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo"
            }

            should("collapse a multiline body back to the header with no dangling blank line") {
                val source = "class Foo\n{\n}"
                val bodyStart = source.indexOf('{')
                val bodyEnd = source.length

                val edit = EmptyClassBodyDeletionSpan.compute(source, bodyStart, bodyEnd)

                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo"
            }

            should("stop trimming at a comment rather than deleting past it") {
                val source = "class Foo /* keep */ {}"
                val bodyStart = source.indexOf('{')
                val bodyEnd = source.length

                val edit = EmptyClassBodyDeletionSpan.compute(source, bodyStart, bodyEnd)

                edit.startOffset shouldBe source.indexOf(" {}")
                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo /* keep */"
            }

            should("not trim before the body when the header is directly adjacent") {
                val source = "class Foo{}"
                val bodyStart = source.indexOf('{')
                val bodyEnd = source.length

                val edit = EmptyClassBodyDeletionSpan.compute(source, bodyStart, bodyEnd)

                edit.startOffset shouldBe bodyStart
                (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo"
            }
        },
    )
