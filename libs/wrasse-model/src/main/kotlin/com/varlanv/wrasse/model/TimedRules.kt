package com.varlanv.wrasse.model

import com.varlanv.wrasse.lang.WPerf

/**
 * Wraps every rule of a file's dispatch so each callback's own time (not its children's — a
 * node rule's enter and exit are measured separately) lands on `rule:<id>` in [WPerf]. Only
 * built when the probe is enabled; the walk itself never checks the probe.
 */
object TimedRules {
    fun wrap(rules: List<WRule>, perf: WPerf): List<WRule> = rules.map { rule ->
        when (rule) {
            is WBufferedNodeRule -> if (rule is ChildLeafHandler) {
                TimedLeafHandlingBufferedNodeRule(rule, perf)
            } else {
                TimedBufferedNodeRule(rule, perf)
            }
            is WNodeRule ->
                if (rule is ChildLeafHandler) TimedLeafHandlingNodeRule(rule, perf) else TimedNodeRule(rule, perf)
            is WLeafRule -> TimedLeafRule(rule, perf)
            is WStreamRule -> TimedStreamRule(rule, perf)
            is WFileRule -> TimedFileRule(rule, perf)
        }
    }
}

private class TimedLeafRule(private val delegate: WLeafRule, private val perf: WPerf) : WLeafRule {
    private val key = "rule:${delegate.id}"
    override val id: String get() = delegate.id
    override val config: WrasseRuleConfig get() = delegate.config
    override val targetTypes: Set<WNodeType> get() = delegate.targetTypes

    override fun beforeFile(ctx: WContext) {
        val started = System.nanoTime()
        delegate.beforeFile(ctx)
        perf.record(key, System.nanoTime() - started)
    }

    override fun afterFile(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.afterFile(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.visitLeaf(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }
}

private open class TimedNodeRule(private val delegate: WNodeRule, private val perf: WPerf) : WNodeRule {
    protected val key = "rule:${delegate.id}"
    override val id: String get() = delegate.id
    override val config: WrasseRuleConfig get() = delegate.config
    override val targetTypes: Set<WNodeType> get() = delegate.targetTypes

    override fun beforeFile(ctx: WContext) {
        val started = System.nanoTime()
        delegate.beforeFile(ctx)
        perf.record(key, System.nanoTime() - started)
    }

    override fun afterFile(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.afterFile(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }

    override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
        val started = System.nanoTime()
        val wantChildren = delegate.enterNode(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
        return wantChildren
    }

    override fun exitNode(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.exitNode(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }
}

private class TimedLeafHandlingNodeRule(
    private val handler: WNodeRule,
    private val perf: WPerf,
) : TimedNodeRule(handler, perf), ChildLeafHandler {
    override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        (handler as ChildLeafHandler).onChildLeaf(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }
}

private open class TimedBufferedNodeRule(
    private val buffered: WBufferedNodeRule,
    private val perf: WPerf,
) : TimedNodeRule(buffered, perf), WBufferedNodeRule {
    override fun exitNode(
        ctx: WContext,
        children: ChildBuffer,
        reporter: WReporter,
    ) {
        val started = System.nanoTime()
        buffered.exitNode(ctx, children, reporter)
        perf.record(key, System.nanoTime() - started)
    }

    override fun exitNode(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        buffered.exitNode(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }
}

private class TimedStreamRule(private val delegate: WStreamRule, private val perf: WPerf) : WStreamRule {
    private val key = "rule:${delegate.id}"
    override val id: String get() = delegate.id
    override val config: WrasseRuleConfig get() = delegate.config

    override fun beforeFile(ctx: WContext) {
        val started = System.nanoTime()
        delegate.beforeFile(ctx)
        perf.record(key, System.nanoTime() - started)
    }

    override fun afterFile(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.afterFile(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.visitLeaf(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }

    override fun enterNode(ctx: WContext) {
        val started = System.nanoTime()
        delegate.enterNode(ctx)
        perf.record(key, System.nanoTime() - started)
    }

    override fun exitNode(ctx: WContext) {
        val started = System.nanoTime()
        delegate.exitNode(ctx)
        perf.record(key, System.nanoTime() - started)
    }
}

private class TimedFileRule(private val delegate: WFileRule, private val perf: WPerf) : WFileRule {
    private val key = "rule:${delegate.id}"
    override val id: String get() = delegate.id
    override val config: WrasseRuleConfig get() = delegate.config

    override fun beforeFile(ctx: WContext) {
        val started = System.nanoTime()
        delegate.beforeFile(ctx)
        perf.record(key, System.nanoTime() - started)
    }

    override fun afterFile(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.afterFile(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }

    override fun visit(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        delegate.visit(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }
}

private class TimedLeafHandlingBufferedNodeRule(
    private val handler: WBufferedNodeRule,
    private val perf: WPerf,
) : TimedBufferedNodeRule(handler, perf), ChildLeafHandler {
    override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
        val started = System.nanoTime()
        (handler as ChildLeafHandler).onChildLeaf(ctx, reporter)
        perf.record(key, System.nanoTime() - started)
    }
}
