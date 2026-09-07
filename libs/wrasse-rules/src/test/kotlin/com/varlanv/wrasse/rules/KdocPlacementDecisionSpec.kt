package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe

class KdocPlacementDecisionSpec : BaseSpec({

    should("not report a KDoc that is a class's own first child") {
        KdocPlacementDecision.decide(WNodeType.CLASS, 0) shouldBe null
    }

    should("report a KDoc that is not a class's own first child") {
        KdocPlacementDecision.decide(WNodeType.CLASS, 2) shouldBe "A KDoc is allowed only at the start of a 'class'"
    }

    should("not report a KDoc that is a value parameter's own first child") {
        KdocPlacementDecision.decide(WNodeType.VALUE_PARAMETER, 0) shouldBe null
    }

    should("report a KDoc that is not a value parameter's own first child") {
        KdocPlacementDecision.decide(WNodeType.VALUE_PARAMETER, 1) shouldBe
            "A KDoc is allowed only at the start of a 'value_parameter'"
    }

    should("report a dangling top-level KDoc") {
        KdocPlacementDecision.decide(WNodeType.FILE, 0) shouldBe "A dangling top-level KDoc is not allowed"
    }

    should("report a KDoc nested inside a disallowed node kind") {
        KdocPlacementDecision.decide(WNodeType.VALUE_ARGUMENT_LIST, 0) shouldBe
            "A KDoc is not allowed inside a 'value_argument_list'"
    }
})
