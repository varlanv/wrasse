package com.varlanv.wrasse.model

interface WReporter {

    fun report(violation: WViolation, rule: WRule)
}
