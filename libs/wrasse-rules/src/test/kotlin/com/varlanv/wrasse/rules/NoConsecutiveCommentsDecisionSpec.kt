package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class NoConsecutiveCommentsDecisionSpec : BaseSpec({

    should("report a KDoc preceded by a KDoc") {
        NoConsecutiveCommentsDecision.decide(WNodeType.KDOC, WNodeType.KDOC, separatedByBlankLine = true) shouldBe
            "a KDoc may not be preceded by a KDoc"
    }

    should("report a block comment preceded by a KDoc even across a blank line") {
        NoConsecutiveCommentsDecision.decide(
            WNodeType.KDOC,
            WNodeType.BLOCK_COMMENT,
            separatedByBlankLine = true,
        ) shouldBe
            "a block comment may not be preceded by a KDoc. Reversed order is allowed though when separated by " +
                "a newline."
    }

    should("report a block comment preceded by a block comment") {
        NoConsecutiveCommentsDecision.decide(
            WNodeType.BLOCK_COMMENT,
            WNodeType.BLOCK_COMMENT,
            separatedByBlankLine = true,
        ) shouldBe "a block comment may not be preceded by a block comment"
    }

    should("not report an EOL comment preceded by an EOL comment") {
        NoConsecutiveCommentsDecision.decide(
            WNodeType.EOL_COMMENT,
            WNodeType.EOL_COMMENT,
            separatedByBlankLine = false,
        ) shouldBe null
    }

    should("not report a mismatched pair separated by a blank line") {
        NoConsecutiveCommentsDecision.decide(
            WNodeType.EOL_COMMENT,
            WNodeType.BLOCK_COMMENT,
            separatedByBlankLine = true,
        ) shouldBe null
    }

    should("report a mismatched pair not separated by a blank line") {
        NoConsecutiveCommentsDecision.decide(
            WNodeType.EOL_COMMENT,
            WNodeType.BLOCK_COMMENT,
            separatedByBlankLine = false,
        ) shouldBe "a block comment may not be preceded by an EOL comment unless separated by a blank line"
    }
})
