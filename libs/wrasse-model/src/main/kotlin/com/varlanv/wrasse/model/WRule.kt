package com.varlanv.wrasse.model

import com.varlanv.wrasse.config.WrasseConfig

interface WRule {
    val id: String
    fun check(file: WFile, config: WrasseConfig): List<WViolation>
}
