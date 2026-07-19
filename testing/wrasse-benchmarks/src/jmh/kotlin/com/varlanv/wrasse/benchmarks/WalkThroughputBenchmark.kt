package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.infra.Blackhole

private class CountingReporter : WReporter {
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

open class WalkThroughputBenchmark {

    @Benchmark
    fun walkWithShippedRules(state: WalkBenchmarkState, blackhole: Blackhole) {
        blackhole.consume(walkCorpus(state.corpus) { state.shippedRuleDispatch() })
    }

    @Benchmark
    fun walkWithNoRules(state: WalkBenchmarkState, blackhole: Blackhole) {
        blackhole.consume(walkCorpus(state.corpus) { state.noRuleDispatch() })
    }

    private fun walkCorpus(corpus: List<CorpusSource>, dispatchFactory: () -> StreamDispatch): Int {
        var total = 0
        for (source in corpus) {
            val ctx = WContext(filePath = source.fileName)
            val reporter = CountingReporter()
            LightTreeStreamAdapter.walk(source.lightSource, ctx, dispatchFactory(), reporter)
            total += reporter.reports.size
        }
        return total
    }
}
