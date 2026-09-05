package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionValue
import com.varlanv.wrasse.model.WRuleOptions
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

/**
 * The per-file cost of instantiating every shipped rule (each with its option defaults; rules
 * with a required option are left out) and building its dispatch (D20).
 */
@State(Scope.Benchmark)
open class RuleInitState {
    lateinit var rules: List<Pair<WUninitializedRule, WrasseRuleConfig>>
    lateinit var groups: List<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>>

    @Setup
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        val facade = Class.forName("com.varlanv.wrasse.plugin.WrasseKotlincPluginMainKt")
        val allRules = facade.getMethod("registeredRules").invoke(null) as List<WUninitializedRule>
        val allGroups = facade.getMethod("registeredRuleGroups").invoke(null) as List<WUninitializedRuleGroup>
        rules = allRules.mapNotNull { rule -> configFor(rule.options)?.let { rule to it } }
        val readyGroups = ArrayList<Pair<WUninitializedRuleGroup, Map<String, WrasseRuleConfig>>>()
        for (group in allGroups) {
            val configs = HashMap<String, WrasseRuleConfig>()
            for (id in group.ids) {
                configs[id] = configFor(group.optionSpecs[id] ?: emptyList()) ?: break
            }
            if (configs.size == group.ids.size) readyGroups.add(group to configs)
        }
        groups = readyGroups
        println("wrasse-benchmarks: rules=${rules.size}/${allRules.size} groups=${groups.size}/${allGroups.size}")
    }

    private fun configFor(specs: List<WRuleOptionSpec>): WrasseRuleConfig? {
        val values = HashMap<String, WRuleOptionValue>()
        for (spec in specs) {
            when (spec) {
                is WRuleOptionSpec.Required -> return null
                is WRuleOptionSpec.Optional -> spec.default?.let { values[spec.name] = it }
            }
        }
        return WrasseRuleConfig(RuleLevel.ERROR, emptyList(), RuleLevel.ERROR, options = WRuleOptions(values))
    }
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class RuleInitBenchmark {
    @Benchmark
    fun initAllShippedRules(state: RuleInitState, blackhole: Blackhole) {
        val initialized = ArrayList<WRule>(state.rules.size + state.groups.size)
        for ((rule, config) in state.rules) initialized.add(rule.initRule(config))
        for ((group, configs) in state.groups) initialized.add(group.initGroup(configs))
        blackhole.consume(StreamDispatch(initialized))
    }
}
