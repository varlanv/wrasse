package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeAdapter
import com.varlanv.wrasse.model.SplitRules
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WViolation
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.backend.common.push

class WrassePlugin(
    private val rules: SplitRules,
) {


    fun checkFile(source: KtLightSourceElement, fileName: String): List<ViolationReport> {
        val wFile = LightTreeAdapter.adapt(source, fileName)
        val violations = mutableListOf<ReportedViolation>()
        val reporter: WReporter = object : WReporter {
            override fun report(violation: WViolation, rule: WRule) {
                violations.push(ReportedViolation(violation = violation, rule = rule))
            }
        }

        if (rules.hasNodeRules) {
            for (node in wFile.root.descendants()) {
                val matching = rules.rulesForType(node.type)
                for (rule in matching) {
                    rule.visit(node = node, reporter = reporter)
                }
            }
        }

        for (rule in rules.fileVisitorRules) {
            rule.visit(file = wFile, reporter = reporter)
        }

        val reports = ArrayList<ViolationReport>(violations.size)
        for (reportedViolation in violations) {
            val rule = reportedViolation.rule
            val violation = reportedViolation.violation
            reports.add(
                ViolationReport(
                    message = "${rule.id}: ${violation.message}",
                    startOffset = violation.node.startOffset,
                    endOffset = violation.node.endOffset,
                    level = rule.config.effectiveLevel,
                )
            )
        }
        return reports
    }

    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }
}

class ReportedViolation(val violation: WViolation, val rule: WRule)
