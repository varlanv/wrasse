package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LongNumericalValuesDecisionSpec : BaseSpec({

    should("group a plain decimal integer literal from the right") {
        val edit = LongNumericalValuesDecision.decideInteger("1000000", 10)

        edit shouldNotBe null
        edit!!.startOffset shouldBe 10
        edit.endOffset shouldBe 17
        edit.replacement shouldBe "1_000_000"
    }

    should("group an uneven-length decimal integer literal from the right") {
        val edit = LongNumericalValuesDecision.decideInteger("1234567", 0)

        edit!!.replacement shouldBe "1_234_567"
    }

    should("keep the L suffix outside the grouped digits") {
        val edit = LongNumericalValuesDecision.decideInteger("1234567890123456L", 0)

        edit!!.replacement shouldBe "1_234_567_890_123_456L"
    }

    should("group a hex literal's digits, keeping the 0x prefix untouched") {
        val edit = LongNumericalValuesDecision.decideInteger("0xFFECDE5E", 0)

        edit!!.replacement shouldBe "0xFF_ECD_E5E"
    }

    should("group a binary literal's digits, keeping the 0b prefix untouched") {
        val edit = LongNumericalValuesDecision.decideInteger("0b110100110011", 0)

        edit!!.replacement shouldBe "0b110_100_110_011"
    }

    should("not report a decimal integer literal at or under the threshold") {
        LongNumericalValuesDecision.decideInteger("100", 0) shouldBe null
    }

    should("not report an integer literal that already contains an underscore") {
        LongNumericalValuesDecision.decideInteger("1000_000", 0) shouldBe null
    }

    should("group a float literal's real part, leaving a short fractional part alone") {
        val edit = LongNumericalValuesDecision.decideFloat("1924345.145", 0)

        edit!!.replacement shouldBe "1_924_345.145"
    }

    should("group a float literal's fractional part from the left, leaving a short real part alone") {
        val edit = LongNumericalValuesDecision.decideFloat("192.312341341344355345", 0)

        edit!!.replacement shouldBe "192.312_341_341_344_355_345"
    }

    should("group both parts of a float literal independently") {
        val edit = LongNumericalValuesDecision.decideFloat("1924345.14511111", 0)

        edit!!.replacement shouldBe "1_924_345.145_111_11"
    }

    should("keep the f suffix outside the grouped fractional digits") {
        val edit = LongNumericalValuesDecision.decideFloat("1924345.145f", 0)

        edit!!.replacement shouldBe "1_924_345.145f"
    }

    should("not report a float literal whose both parts are at or under the threshold") {
        LongNumericalValuesDecision.decideFloat("192.312", 0) shouldBe null
    }

    should("not report a float literal that already contains an underscore") {
        LongNumericalValuesDecision.decideFloat("192.111_111_1", 0) shouldBe null
    }

    should("not report a float literal in scientific notation") {
        LongNumericalValuesDecision.decideFloat("1.234567e10", 0) shouldBe null
    }

    should("not report a float literal with no decimal point at all") {
        LongNumericalValuesDecision.decideFloat("1234567", 0) shouldBe null
    }
})
