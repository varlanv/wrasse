package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.model.WRule
import org.jetbrains.kotlin.KtLightSourceElement

class WrassePlugin(
    private val config: WrasseConfig,
    private val rules: List<WRule>
) {

    fun checkFile(source: KtLightSourceElement, fileName: String): List<ViolationReport> {
        val wFile = LightTreeAdapter.adapt(source, fileName)
        val violations = mutableListOf<ViolationReport>()
        for (rule in rules) {
            for (violation in rule.check(wFile, config)) {
                violations.add(
                    ViolationReport(
                        message = "${violation.ruleId}: ${violation.message}",
                        startOffset = violation.node.startOffset,
                        endOffset = violation.node.endOffset,
                        severity = violation.severity,
                    )
                )
            }
        }

        return violations
    }

    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }
}
