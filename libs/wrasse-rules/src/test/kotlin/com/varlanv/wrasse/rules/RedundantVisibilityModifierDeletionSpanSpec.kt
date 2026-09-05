package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class RedundantVisibilityModifierDeletionSpanSpec : BaseSpec({

    should("delete public and the single following space") {
        val source = "public class Foo"
        val publicStart = 0
        val publicEnd = source.indexOf(" class")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = false,
        )

        edit.shouldNotBeNull()
        edit.startOffset shouldBe publicStart
        edit.endOffset shouldBe source.indexOf("class")
        edit.replacement shouldBe ""
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo"
    }

    should("collapse a newline and indentation between public and the next keyword") {
        val source = "public\n    class Foo"
        val publicStart = 0
        val publicEnd = source.indexOf("\n")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = false,
        )

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "class Foo"
    }

    should("only remove public itself, leaving a following modifier keyword untouched") {
        val source = "public open class Foo"
        val publicStart = 0
        val publicEnd = source.indexOf(" open")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = false,
        )

        edit.shouldNotBeNull()
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "open class Foo"
    }

    should("bail with no edit when the caller already found a comment inside the modifier list") {
        val source = "public class Foo"
        val publicStart = 0
        val publicEnd = source.indexOf(" class")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = true,
        )

        edit shouldBe null
    }

    should("bail with no edit when a line comment immediately follows the trailing whitespace") {
        val source = "public // trailing\n    fun foo() {}"
        val publicStart = 0
        val publicEnd = source.indexOf(" //")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = false,
        )

        edit shouldBe null
    }

    should("bail with no edit when a block comment immediately follows the trailing whitespace") {
        val source = "public /* c */ fun foo() {}"
        val publicStart = 0
        val publicEnd = source.indexOf(" /*")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = false,
        )

        edit shouldBe null
    }

    should("not trim before public when directly adjacent to the previous token") {
        val source = "@Ann public class Foo"
        val publicStart = source.indexOf("public")
        val publicEnd = source.indexOf(" class")

        val edit = RedundantVisibilityModifierDeletionSpan.compute(
            source,
            publicStart,
            publicEnd,
            hasCommentInList = false,
        )

        edit.shouldNotBeNull()
        edit.startOffset shouldBe publicStart
        (source.substring(0, edit.startOffset) + source.substring(edit.endOffset)) shouldBe "@Ann class Foo"
    }
})
