package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.model.RuleLevel

class ViolationReport(
    val message: String,
    val startOffset: Int,
    val endOffset: Int,
    val level: RuleLevel,
)
