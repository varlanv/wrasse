package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class ForbiddenCommentDecisionSpec :
    BaseSpec(
        {

            should("report a comment containing TODO:") {
                ForbiddenCommentDecision.decide("// TODO: fix this") shouldBe
                    "This comment contains 'TODO:', which is forbidden in production code"
            }

            should("report a comment containing FIXME:") {
                ForbiddenCommentDecision.decide("/* FIXME: hack */") shouldBe
                    "This comment contains 'FIXME:', which is forbidden in production code"
            }

            should("report a comment containing STOPSHIP:") {
                ForbiddenCommentDecision.decide("/** STOPSHIP: */") shouldBe
                    "This comment contains 'STOPSHIP:', which is forbidden in production code"
            }

            should("not report an ordinary comment") {
                ForbiddenCommentDecision.decide("// just a note") shouldBe null
            }

            should("report the first matching marker when several are present") {
                ForbiddenCommentDecision.decide("// FIXME: then TODO: too") shouldBe
                    "This comment contains 'FIXME:', which is forbidden in production code"
            }
        },
    )
