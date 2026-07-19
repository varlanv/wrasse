package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class WalkThroughputBenchmark {

    @Benchmark
    fun walkWithShippedRules(state: WalkBenchmarkState, blackhole: Blackhole) {
        val ctx = WContext(filePath = "benchmark.kt")
        val reporter = CountingReporter()
        LightTreeStreamAdapter.walk(state.lightSource, ctx, state.shippedRuleDispatch(), reporter)
        blackhole.consume(reporter.reports.size)
    }

    @Benchmark
    fun walkWithNoRules(state: WalkBenchmarkState, blackhole: Blackhole) {
        val ctx = WContext(filePath = "benchmark.kt")
        val reporter = CountingReporter()
        LightTreeStreamAdapter.walk(state.lightSource, ctx, state.noRuleDispatch(), reporter)
        blackhole.consume(reporter.reports.size)
    }
}
