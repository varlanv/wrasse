package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.config.WrasseSeverity

class ViolationReport(
    val message: String,
    val startOffset: Int,
    val endOffset: Int,
    val severity: WrasseSeverity,
)
