package com.varlanv.wrasse.rules

import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class BackingPropertyNamingDecisionSpec :
    BaseSpec(
        {
            should("ignore a property whose name has no leading underscore") {
                BackingPropertyNamingDecision.decide("foo", hasOverride = false, correlatedMemberIsPublic = null) shouldBe null
            }

            should("ignore the bare underscore identifier") {
                BackingPropertyNamingDecision.decide("_", hasOverride = false, correlatedMemberIsPublic = null) shouldBe null
            }

            should("ignore an override regardless of shape") {
                BackingPropertyNamingDecision.decide("_Foo", hasOverride = true, correlatedMemberIsPublic = null) shouldBe null
            }

            should("reject a leading underscore followed by an uppercase letter") {
                BackingPropertyNamingDecision.decide("_Foo", hasOverride = false, correlatedMemberIsPublic = null) shouldBe
                    BackingPropertyNamingDecision.SHAPE_MESSAGE
            }

            should("accept a well-shaped backing property with no correlated member reachable") {
                BackingPropertyNamingDecision.decide("_foo", hasOverride = false, correlatedMemberIsPublic = null) shouldBe null
            }

            should("accept a well-shaped backing property whose correlated member is public") {
                BackingPropertyNamingDecision.decide("_foo", hasOverride = false, correlatedMemberIsPublic = true) shouldBe null
            }

            should("reject a well-shaped backing property whose correlated member is not public") {
                BackingPropertyNamingDecision.decide("_foo", hasOverride = false, correlatedMemberIsPublic = false) shouldBe
                    BackingPropertyNamingDecision.VISIBILITY_MESSAGE
            }
        },
    )
