package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.format.DocBuilder
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
        blackhole.consume(walkCorpus(state.corpus, { state.shippedRuleDispatch() }))
    }

    @Benchmark
    fun walkWithNoRules(state: WalkBenchmarkState, blackhole: Blackhole) {
        blackhole.consume(walkCorpus(state.corpus, { state.noRuleDispatch() }))
    }

    @Benchmark
    fun walkWithBufferedRules(state: WalkBenchmarkState, blackhole: Blackhole) {
        blackhole.consume(walkCorpus(state.corpus, { state.bufferedRuleDispatch() }))
    }

    @Benchmark
    fun walkWithFormat(state: WalkBenchmarkState, blackhole: Blackhole) {
        blackhole.consume(
            walkCorpus(state.corpus, { state.formatDispatch() }) { dispatch, ctx, reporter ->
                (dispatch.allRules.last() as DocBuilder).finish(ctx, reporter)
            },
        )
    }

    private fun walkCorpus(
        corpus: List<CorpusSource>,
        dispatchFactory: () -> StreamDispatch,
        afterWalk: (StreamDispatch, WContext, WReporter) -> Unit = { _, _, _ -> },
    ): Int {
        var total = 0
        for (source in corpus) {
            val ctx = WContext(filePath = source.fileName)
            val reporter = CountingReporter()
            val dispatch = dispatchFactory()
            LightTreeStreamAdapter.walk(source.lightSource, ctx, dispatch, reporter)
            afterWalk(dispatch, ctx, reporter)
            total += reporter.reports.size
        }
        return total
    }
}
