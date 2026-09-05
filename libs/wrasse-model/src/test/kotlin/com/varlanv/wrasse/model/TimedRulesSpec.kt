package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.NoopPerf
import com.varlanv.wrasse.lang.PerfRecorder
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private val config = WrasseRuleConfig(level = RuleLevel.ERROR, exclude = emptyList(), effectiveLevel = RuleLevel.ERROR)

private class CountingLeafRule : WLeafRule {
    var visits = 0
    override val id = "leaf"
    override val config = com.varlanv.wrasse.model.config
    override val targetTypes = setOf(WNodeType.IDENTIFIER)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        visits++
    }
}

private class CountingBufferedRule : WBufferedNodeRule {
    var exits = 0
    override val id = "buffered"
    override val config = com.varlanv.wrasse.model.config
    override val targetTypes = setOf(WNodeType.FUN)

    override fun exitNode(
        ctx: WContext,
        children: ChildBuffer,
        reporter: WReporter,
    ) {
        exits++
    }
}

private class SilentReporter : WReporter {
    override val reports = mutableListOf<ViolationReport>()

    override fun report(
        ruleId: String,
        message: String,
        startOffset: Int,
        endOffset: Int,
        rule: WRule,
        edits: List<WEdit>,
    ) {}
}

class TimedRulesSpec : BaseSpec({

    should("return the rules untouched when the probe is disabled") {
        val leaf = CountingLeafRule()
        val ruleSet = WRuleSet(emptyList(), emptyList())
        val dispatch = ruleSet.dispatchForFile(alwaysOn = listOf(leaf), perf = NoopPerf) { false }
        dispatch.allRules[0] shouldBeSameInstanceAs leaf
    }

    should("time every callback of a wrapped rule under rule:<id>, keeping the rule's kind and identity") {
        val leaf = CountingLeafRule()
        val buffered = CountingBufferedRule()
        val perf = PerfRecorder()
        val wrapped = TimedRules.wrap(listOf(leaf, buffered), perf)
        val timedLeaf = wrapped[0] as WLeafRule
        val timedBuffered = wrapped[1] as WBufferedNodeRule
        timedLeaf.id shouldBe "leaf"
        timedLeaf.targetTypes shouldBe setOf(WNodeType.IDENTIFIER)
        timedBuffered.id shouldBe "buffered"

        val ctx = WContext(filePath = "test.kt")
        val reporter = SilentReporter()
        timedLeaf.beforeFile(ctx)
        timedLeaf.visitLeaf(ctx, reporter)
        timedLeaf.visitLeaf(ctx, reporter)
        timedLeaf.afterFile(ctx, reporter)
        timedBuffered.enterNode(ctx, reporter) shouldBe true
        timedBuffered.exitNode(ctx, ChildBuffer(), reporter)

        leaf.visits shouldBe 2
        buffered.exits shouldBe 1
        perf["rule:leaf"]!!.count shouldBe 4
        perf["rule:buffered"]!!.count shouldBe 2
    }
})
