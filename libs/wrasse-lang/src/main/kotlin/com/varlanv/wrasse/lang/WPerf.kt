package com.varlanv.wrasse.lang

/**
 * Performance probe every phase reports into. [create] is the only constructor site: it picks
 * [NoopPerf] for an ordinary run and [PerfRecorder] only for a run that asked for timings, then
 * calls [init] on that one instance. The recorder refuses `init(false)`, so its class can only
 * ever be loaded by an active run. Callers on hot paths check [enabled] first; a disabled probe
 * costs one field read.
 */
interface WPerf {
    val enabled: Boolean

    /** Called once, right after construction, with whether this run asked for timings. */
    fun init(active: Boolean)

    /** Elapsed-time key: adds [nanos] to [key]'s total and bumps its count. */
    fun record(key: String, nanos: Long)

    /** Quantity key: adds [amount] to [key]'s total and bumps its count. */
    fun add(key: String, amount: Long)

    companion object {
        fun create(active: Boolean): WPerf {
            val perf: WPerf = if (active) PerfRecorder() else NoopPerf
            perf.init(active)
            return perf
        }
    }
}

object NoopPerf : WPerf {
    override val enabled: Boolean = false

    override fun init(active: Boolean) {}

    override fun record(key: String, nanos: Long) {}

    override fun add(key: String, amount: Long) {}
}
