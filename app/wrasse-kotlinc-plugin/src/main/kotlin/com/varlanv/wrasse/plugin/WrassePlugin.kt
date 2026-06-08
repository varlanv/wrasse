package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.config.WrasseConfig
import com.varlanv.wrasse.config.WrasseSeverity
import com.varlanv.wrasse.model.SplitRules
import com.varlanv.wrasse.model.WViolation
import org.jetbrains.kotlin.KtLightSourceElement

class WrassePlugin(
    private val config: WrasseConfig,
    private val rules: SplitRules,
    val severity: WrasseSeverity,
) {

    fun checkFile(source: KtLightSourceElement, fileName: String): List<ViolationReport> {
        val wFile = LightTreeAdapter.adapt(source, fileName)
        val violations = mutableListOf<WViolation>()

        if (rules.hasNodeRules) {
            for (node in wFile.root.descendants()) {
                val matching = rules.rulesForType(node.type)
                for (rule in matching) {
                    rule.visit(node, violations)
                }
            }
        }

        for (rule in rules.fileVisitorRules) {
            rule.visit(wFile, violations)
        }

        val reports = ArrayList<ViolationReport>(violations.size)
        for (violation in violations) {
            reports.add(
                ViolationReport(
                    message = "${violation.ruleId}: ${violation.message}",
                    startOffset = violation.node.startOffset,
                    endOffset = violation.node.endOffset,
                )
            )
        }
        return reports
    }

    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }
}
