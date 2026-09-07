package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NoSingleLineBlockCommentDecisionSpec : BaseSpec({

    should("report a single-line block comment followed by nothing but a newline") {
        NoSingleLineBlockCommentDecision.decide("/* text */", followedByCodeOnSameLine = false) shouldBe
            NoSingleLineBlockCommentDecision.MESSAGE
    }

    should("not report a single-line block comment followed by code on the same line") {
        NoSingleLineBlockCommentDecision.decide("/* text */", followedByCodeOnSameLine = true) shouldBe null
    }

    should("not report a multi-line block comment") {
        NoSingleLineBlockCommentDecision.decide("/* line one\nline two */", followedByCodeOnSameLine = false) shouldBe
            null
    }

    should("replace with an EOL comment, trimming one leading and one trailing space") {
        NoSingleLineBlockCommentDecision.replacement("/* text */") shouldBe "// text"
    }

    should("trim only one leading space, keeping the rest verbatim") {
        NoSingleLineBlockCommentDecision.replacement("/*  text  */") shouldBe "//  text "
    }

    should("add no space when the content already touches the delimiters") {
        NoSingleLineBlockCommentDecision.replacement("/*text*/") shouldBe "// text"
    }

    should("preserve an embedded // verbatim") {
        NoSingleLineBlockCommentDecision.replacement("/* see // note */") shouldBe "// see // note"
    }

    should("decline an empty comment") {
        NoSingleLineBlockCommentDecision.replacement("/* */") shouldBe null
    }

    should("decline a comment with only whitespace content") {
        NoSingleLineBlockCommentDecision.replacement("/*   */") shouldBe null
    }

    should("decline the smallest possible empty comment") {
        NoSingleLineBlockCommentDecision.replacement("/**/") shouldBe null
    }

    should("decline an unterminated block comment at end of file") {
        NoSingleLineBlockCommentDecision.replacement("/*") shouldBe null
    }

    should("decline text too short to hold both delimiters") {
        NoSingleLineBlockCommentDecision.replacement("/**") shouldBe null
        NoSingleLineBlockCommentDecision.replacement("") shouldBe null
    }

    should("decline an unterminated block comment with content but no closing delimiter") {
        NoSingleLineBlockCommentDecision.replacement("/* abc") shouldBe null
    }
})
