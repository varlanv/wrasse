package com.varlanv.wrasse.lang

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The recording [WPerf]: one [Entry] per key with count, total and max, in first-seen order.
 * Keys are namespaced by prefix — `phase:` and `rule:` and `apply:` hold nanoseconds,
 * `file:<path>` holds a file's total nanoseconds, `count:` holds plain quantities — which is
 * what [PerfReport] groups by. [serialize]/[parse] round-trip the entries as tab-separated lines.
 */
class PerfRecorder : WPerf {
    class Entry(val key: String) {
        var count: Long = 0
        var total: Long = 0
        var max: Long = 0
    }

    private val entries = LinkedHashMap<String, Entry>()

    override val enabled: Boolean = true

    override fun init(active: Boolean) {
        check(active) { "PerfRecorder must only be instantiated for a run that asked for timings" }
    }

    override fun record(key: String, nanos: Long) = add(key, nanos)

    override fun add(key: String, amount: Long) {
        val entry = entries.getOrPut(key) { Entry(key) }
        entry.count++
        entry.total += amount
        if (amount > entry.max) entry.max = amount
    }

    fun entries(): Collection<Entry> = entries.values

    operator fun get(key: String): Entry? = entries[key]

    fun serialize(): String {
        val sb = StringBuilder()
        for (entry in entries.values) {
            sb
                .append(entry.key)
                .append('\t')
                .append(entry.count)
                .append('\t')
                .append(entry.total)
                .append('\t')
                .append(entry.max)
                .append('\n')
        }
        return sb.toString()
    }

    companion object {
        fun parse(text: String): PerfRecorder {
            val recorder = PerfRecorder()
            for (line in text.split('\n')) {
                val parts = line.split('\t')
                if (parts.size != 4) continue
                val entry = Entry(parts[0])
                entry.count = parts[1].toLongOrNull() ?: continue
                entry.total = parts[2].toLongOrNull() ?: continue
                entry.max = parts[3].toLongOrNull() ?: continue
                recorder.entries[entry.key] = entry
            }
            return recorder
        }
    }
}

/**
 * Persists a compilation's [PerfRecorder] as `<dir>/wrasse-perf.txt` (a `# <title>` header line,
 * then [PerfRecorder.serialize]) so the apply step, which runs later in another JVM, can print
 * it. [collect] walks a tree, parses every such file and deletes it.
 */
object PerfStore {
    const val FILE_NAME = "wrasse-perf.txt"

    fun write(
        dir: Path,
        title: String,
        perf: WPerf,
    ) {
        if (!perf.enabled || perf !is PerfRecorder) return
        try {
            Files.createDirectories(dir)
            Files.write(dir.resolve(FILE_NAME), ("# $title\n" + perf.serialize()).toByteArray(StandardCharsets.UTF_8))
        } catch (_: IOException) {
            return
        }
    }

    fun collect(root: Path): List<Pair<String, PerfRecorder>> {
        if (!Files.isDirectory(root)) return emptyList()
        val files = Files.walk(root).use { walk ->
            walk.filter { Files.isRegularFile(it) && it.fileName.toString() == FILE_NAME }.toList()
        }
        val reports = ArrayList<Pair<String, PerfRecorder>>(files.size)
        for (file in files) {
            val text = Files.readString(file)
            val newline = text.indexOf('\n')
            val header = if (newline < 0) text else text.substring(0, newline)
            val title = header.removePrefix("# ")
            val body = if (newline < 0) "" else text.substring(newline + 1)
            reports.add(title to PerfRecorder.parse(body))
            Files.deleteIfExists(file)
        }
        return reports
    }
}

/**
 * Renders a [PerfRecorder] as the console report: counters on the title line, then phases in
 * recording order, rules sorted by total time with their share of the walk, apply steps, and the
 * ten slowest files.
 */
object PerfReport {
    private const val SLOWEST_FILES = 10

    fun render(title: String, recorder: PerfRecorder): String {
        val sb = StringBuilder()
        sb.append("wrasse performance: ").append(title)
        val counters = recorder.entries().filter { it.key.startsWith("count:") }
        if (counters.isNotEmpty()) {
            sb.append(" (")
            sb.append(counters.joinToString(", ") { "${it.key.removePrefix("count:")} ${it.total}" })
            sb.append(')')
        }
        sb.append('\n')

        val phases = recorder.entries().filter { it.key.startsWith("phase:") }
        val totalPhase = recorder["phase:total"]?.total ?: phases.sumOf { it.total }
        section(sb, "phase", phases, shareOf = totalPhase, callsLabel = "calls")

        val rules = recorder.entries().filter { it.key.startsWith("rule:") }.sortedByDescending { it.total }
        val walk = recorder["phase:walk"]?.total ?: rules.sumOf { it.total }
        section(sb, "rule", rules, shareOf = walk, callsLabel = "calls")

        val apply = recorder.entries().filter { it.key.startsWith("apply:") }
        val applyTotal = recorder["apply:total"]?.total ?: apply.sumOf { it.total }
        section(sb, "apply", apply, shareOf = applyTotal, callsLabel = "calls")

        val files = recorder.entries().filter { it.key.startsWith("file:") }.sortedByDescending { it.total }
        if (files.isNotEmpty()) {
            sb.append("slowest files\n")
            for (entry in files.take(SLOWEST_FILES)) {
                sb
                    .append("  ")
                    .append(millis(entry.total).padStart(10))
                    .append("  ")
                    .append(entry.key.removePrefix("file:"))
                    .append('\n')
            }
        }
        return sb.toString()
    }

    private fun section(
        sb: StringBuilder,
        name: String,
        entries: List<PerfRecorder.Entry>,
        shareOf: Long,
        callsLabel: String,
    ) {
        if (entries.isEmpty()) return
        sb.append(name.padEnd(30)).append("total".padStart(11)).append(callsLabel.padStart(9))
        sb.append("avg".padStart(11)).append("max".padStart(11)).append("share".padStart(8)).append('\n')
        for (entry in entries) {
            val avg = if (entry.count == 0L) 0L else entry.total / entry.count
            val share = if (shareOf <= 0L) "-" else String.format("%.1f%%", entry.total * 100.0 / shareOf)
            sb.append("  ").append(entry.key.substringAfter(':').padEnd(28))
            sb.append(millis(entry.total).padStart(11)).append(entry.count.toString().padStart(9))
            sb
                .append(micros(avg).padStart(11))
                .append(micros(entry.max).padStart(11))
                .append(share.padStart(8))
                .append('\n')
        }
    }

    private fun millis(nanos: Long): String = String.format("%.1f ms", nanos / 1_000_000.0)

    private fun micros(nanos: Long): String = if (nanos >= 1_000_000L) {
        String.format("%.1f ms", nanos / 1_000_000.0)
    } else {
        String.format("%.1f us", nanos / 1_000.0)
    }
}
