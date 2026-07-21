package com.varlanv.wrasse.model

/** A reported violation with its source location and severity. Produced by [WReporter]. */
class ViolationReport(val message: String, val startOffset: Int, val endOffset: Int, val level: RuleLevel)
