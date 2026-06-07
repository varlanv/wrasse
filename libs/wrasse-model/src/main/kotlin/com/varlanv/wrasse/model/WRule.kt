package com.varlanv.wrasse.model

interface WRule {
    val id: String
    fun check(file: WFile): List<WViolation>
}
