package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private class CountingFileRule(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
    var visitCount = 0

    override fun visit(ctx: WContext, reporter: WReporter) {
        visitCount++
    }
}

private class RecordingRule(override val id: String) : WUninitializedRule {
    var initCount = 0
        private set

    override fun initRule(config: WrasseRuleConfig): WRule {
        initCount++
        return CountingFileRule(id, config)
    }
}

private class QualifiedUsagesRequiringRule(override val id: String) : WUninitializedRule {
    override val requiresQualifiedUsages: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WRule = CountingFileRule(id, config)
}

private class RecordingGroup(
    override val ids: Set<String>,
    private val requiresQualifiedUsagesFlag: Boolean,
) : WUninitializedRuleGroup {
    override fun requiresQualifiedUsages(enabledIds: Set<String>): Boolean = requiresQualifiedUsagesFlag

    override fun initGroup(
        configs: Map<String, WrasseRuleConfig>,
    ): WRule = CountingFileRule(ids.first(), configs.values.first())
}

private class NoopReporter : WReporter {
    override val reports = mutableListOf<ViolationReport>()

    override fun report(
        ruleId: String,
        message: String,
        startOffset: Int,
        endOffset: Int,
        rule: WRule,
        edits: List<WEdit>,
    ) {
        reports.add(ViolationReport(message, startOffset, endOffset, rule.config.effectiveLevel))
    }
}

private fun errorConfig(
    exclude: List<java.nio.file.PathMatcher> = emptyList(),
) = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = exclude, effectiveLevel = RuleLevel.ERROR)

class WRuleSetSpec : BaseSpec({

    should("instantiate a fresh rule instance on every dispatchForFile call") {
        val recording = RecordingRule("sample-rule")
        val ruleSet = WRuleSet(listOf(recording to errorConfig()))

        val dispatch1 = ruleSet.dispatchForFile { false }
        val dispatch2 = ruleSet.dispatchForFile { false }

        recording.initCount shouldBe 2
        dispatch1.fileRules shouldHaveSize 1
        dispatch2.fileRules shouldHaveSize 1
        (dispatch1.fileRules[0] === dispatch2.fileRules[0]) shouldBe false
    }

    should("never instantiate a rule excluded for the current file") {
        val included = RecordingRule("included-rule")
        val excluded = RecordingRule("excluded-rule")
        val includedConfig = errorConfig()
        val excludedConfig = errorConfig()
        val ruleSet = WRuleSet(listOf(included to includedConfig, excluded to excludedConfig))

        val dispatch = ruleSet.dispatchForFile { config -> config === excludedConfig }

        included.initCount shouldBe 1
        excluded.initCount shouldBe 0
        dispatch.fileRules.map { it.id } shouldBe listOf("included-rule")
    }

    should("keep mutable per-file rule state independent across two dispatchForFile calls") {
        val recording = RecordingRule("sample-rule")
        val ruleSet = WRuleSet(listOf(recording to errorConfig()))
        val ctx = WContext(filePath = "file1.kt")
        val reporter = NoopReporter()

        val rule1 = ruleSet.dispatchForFile { false }.fileRules[0] as CountingFileRule
        rule1.visit(ctx, reporter)
        rule1.visit(ctx, reporter)
        rule1.visitCount shouldBe 2

        val rule2 = ruleSet.dispatchForFile { false }.fileRules[0] as CountingFileRule
        rule2.visitCount shouldBe 0
        rule2.visit(ctx, reporter)
        rule2.visitCount shouldBe 1
        rule1.visitCount shouldBe 2
    }

    should("stay false when no rule or group opts into requiresQualifiedUsages") {
        val ruleSet = WRuleSet(
            listOf(RecordingRule("plain-rule") to errorConfig()),
            listOf(
                RecordingGroup(setOf("group-rule"), requiresQualifiedUsagesFlag = false) to
                    mapOf("group-rule" to errorConfig()),
            ),
        )

        ruleSet.requiresQualifiedUsages shouldBe false
    }

    should("aggregate requiresQualifiedUsages true when a plain rule opts in") {
        val ruleSet = WRuleSet(listOf(QualifiedUsagesRequiringRule("qualified-rule") to errorConfig()))

        ruleSet.requiresQualifiedUsages shouldBe true
    }

    should("aggregate requiresQualifiedUsages true when a group opts in") {
        val group = RecordingGroup(setOf("group-rule"), requiresQualifiedUsagesFlag = true)
        val ruleSet = WRuleSet(emptyList(), listOf(group to mapOf("group-rule" to errorConfig())))

        ruleSet.requiresQualifiedUsages shouldBe true
    }
})
