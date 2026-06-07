package com.varlanv.wrasse.model

import com.varlanv.wrasse.config.WrasseSeverity

class WViolation(
    val ruleId: String,
    val message: String,
    val node: WNode,
    val severity: WrasseSeverity,
)
