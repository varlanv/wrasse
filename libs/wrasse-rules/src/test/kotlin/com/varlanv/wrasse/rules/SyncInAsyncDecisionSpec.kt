package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class SyncInAsyncDecisionSpec :
    BaseSpec(
        {

            should("report runBlocking reached from a governing async context") {
                SyncInAsyncDecision.decide(isRunBlockingTrailingLambdaCall = true, hasGoverningAsyncContext = true) shouldBe
                    SyncInAsyncDecision.MESSAGE
            }

            should("not report runBlocking with no governing async context") {
                SyncInAsyncDecision.decide(isRunBlockingTrailingLambdaCall = true, hasGoverningAsyncContext = false) shouldBe null
            }

            should("not report when the call is not the runBlocking trailing-lambda shape") {
                SyncInAsyncDecision.decide(isRunBlockingTrailingLambdaCall = false, hasGoverningAsyncContext = true) shouldBe null
            }
        },
    )
