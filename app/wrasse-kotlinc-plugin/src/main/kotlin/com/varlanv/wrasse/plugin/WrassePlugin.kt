package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WViolation
import org.jetbrains.kotlin.KtLightSourceElement

class WrassePlugin(
    private val config: WrasseConfig,
    private val rules: List<WRule>
) {

    fun checkFile(source: KtLightSourceElement, fileName: String): List<ViolationReport> {
        val wFile = LightTreeAdapter.adapt(source, fileName)
        val upstreamViolations = mutableListOf<ViolationReport>()
        val downstreamViolations = mutableListOf<WViolation>()
        for (rule in rules) {
            rule.check(wFile, downstreamViolations)
        }
        for (violation in downstreamViolations) {
            upstreamViolations.add(
                ViolationReport(
                    message = "${violation.ruleId}: ${violation.message}",
                    startOffset = violation.node.startOffset,
                    endOffset = violation.node.endOffset,
                    severity = violation.severity,
                )
            )
        }
        return upstreamViolations
    }

    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }
}
