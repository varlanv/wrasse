package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import org.jetbrains.kotlin.KtLightSourceElement

class WrassePlugin(
    private val dispatch: StreamDispatch,
) {

    fun checkFile(source: KtLightSourceElement, fileName: String): List<ViolationReport> {
        val reporter = object : WReporter {
            override val reports = mutableListOf<ViolationReport>()

            override fun report(ruleId: String, message: String, startOffset: Int, endOffset: Int, rule: WRule) {
                reports.add(
                    ViolationReport(
                        message = "${rule.id}: $message",
                        startOffset = startOffset,
                        endOffset = endOffset,
                        level = rule.config.effectiveLevel,
                    )
                )
            }
        }
        LightTreeStreamAdapter.walk(
            source = source,
            filePath = fileName,
            dispatch = dispatch,
            reporter = reporter
        )
        return reporter.reports
    }

    /** Stub for the FirFunctionCallChecker hook. Will dispatch to SemanticWRules once the resolution facade lands (Phase B.3). */
    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }
}
