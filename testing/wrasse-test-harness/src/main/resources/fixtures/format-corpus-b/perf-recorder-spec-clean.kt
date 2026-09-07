package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class PerfRecorderSpec : BaseSpec({

    should("create the no-op probe for an inactive run and the recorder for an active one") {
        val inactive = WPerf.create(active = false)
        inactive.enabled shouldBe false
        (inactive === NoopPerf) shouldBe true
        val active = WPerf.create(active = true)
        active.enabled shouldBe true
        (active is PerfRecorder) shouldBe true
    }

    should("refuse to initialize the recorder for an inactive run") {
        val failure = shouldThrow<IllegalStateException> { PerfRecorder().init(active = false) }
        failure.message shouldBe "PerfRecorder must only be instantiated for a run that asked for timings"
    }

    should("accumulate count, total and max per key in first-seen order") {
        val recorder = PerfRecorder()
        recorder.record("phase:walk", 10)
        recorder.record("rule:a", 3)
        recorder.record("phase:walk", 30)
        recorder.add("count:files", 1)
        recorder.add("count:files", 1)
        recorder.entries().map { it.key } shouldBe listOf("phase:walk", "rule:a", "count:files")
        val walk = recorder["phase:walk"]!!
        walk.count shouldBe 2
        walk.total shouldBe 40
        walk.max shouldBe 30
        recorder["count:files"]!!.total shouldBe 2
    }

    should("round-trip through serialize and parse, skipping malformed lines") {
        val recorder = PerfRecorder()
        recorder.record("phase:walk", 40)
        recorder.record("rule:a", 3)
        val text = recorder.serialize()
        text shouldBe "phase:walk\t1\t40\t40\nrule:a\t1\t3\t3\n"
        val parsed = PerfRecorder.parse(text + "garbage\nx\ty\tz\tw\n")
        parsed.entries().map { it.key } shouldBe listOf("phase:walk", "rule:a")
        parsed["phase:walk"]!!.total shouldBe 40
    }

    should("persist under a title and collect-and-delete from a tree") {
        useTempDir { root ->
            val main = root.resolve("main")
            val recorder = PerfRecorder()
            recorder.record("phase:total", 5_000_000)
            PerfStore.write(main, "compile main", recorder)
            PerfStore.write(root.resolve("test"), "compile test", NoopPerf)
            Files.exists(main.resolve(PerfStore.FILE_NAME)) shouldBe true
            Files.exists(root.resolve("test").resolve(PerfStore.FILE_NAME)) shouldBe false

            val collected = PerfStore.collect(root)
            collected.map { it.first } shouldBe listOf("compile main")
            collected[0].second["phase:total"]!!.total shouldBe 5_000_000
            Files.exists(main.resolve(PerfStore.FILE_NAME)) shouldBe false
            PerfStore.collect(root) shouldBe emptyList()
        }
    }

    should("render counters, phases with their share of the total, rules by descending time, and slowest files") {
        val recorder = PerfRecorder()
        recorder.add("count:files", 2)
        recorder.record("phase:walk", 6_000_000)
        recorder.record("phase:total", 8_000_000)
        recorder.record("rule:fast", 500_000)
        recorder.record("rule:slow", 4_500_000)
        recorder.record("rule:slow", 1_000_000)
        recorder.record("file:/a/One.kt", 5_000_000)
        recorder.record("file:/a/Two.kt", 3_000_000)
        PerfReport.render("compile main", recorder) shouldBe
            "wrasse performance: compile main (files 2)\n" +
                "phase                               total    calls        avg        max   share\n" +
                "  walk                             6.0 ms        1     6.0 ms     6.0 ms   75.0%\n" +
                "  total                            8.0 ms        1     8.0 ms     8.0 ms  100.0%\n" +
                "rule                                total    calls        avg        max   share\n" +
                "  slow                             5.5 ms        2     2.8 ms     4.5 ms   91.7%\n" +
                "  fast                             0.5 ms        1   500.0 us   500.0 us    8.3%\n" +
                "slowest files\n" +
                "      5.0 ms  /a/One.kt\n" +
                "      3.0 ms  /a/Two.kt\n"
    }
})

// fixture-option: trailing-newline
// fixture-aux-file: aux/Stubs.kt
// fixture-aux-file: aux/Kotest.kt
// fixture-aux-file: aux/KotestCollections.kt
// fixture-aux-file: aux/KotestNulls.kt
// fixture-aux-file: aux/KotestTypes.kt
// fixture-aux-file: aux/KotestAssertions.kt
// fixture-aux-file: aux/KotestThrowables.kt
// fixture-aux-file: aux/lang/ConfigValueJsonc.kt
// fixture-aux-file: aux/lang/FileEdits.kt
// fixture-aux-file: aux/lang/FileWalkUp.kt
// fixture-aux-file: aux/lang/FormatRequest.kt
// fixture-aux-file: aux/lang/HexEncoding.kt
// fixture-aux-file: aux/lang/PerfRecorder.kt
// fixture-aux-file: aux/lang/SafeProperties.kt
// fixture-aux-file: aux/lang/Sha256.kt
// fixture-aux-file: aux/lang/StringSlice.kt
// fixture-aux-file: aux/lang/WEdit.kt
// fixture-aux-file: aux/lang/WPatchApplier.kt
// fixture-aux-file: aux/lang/WPatchReader.kt
// fixture-aux-file: aux/lang/WPatchStore.kt
// fixture-aux-file: aux/lang/WPatchWriter.kt
// fixture-aux-file: aux/lang/WPerf.kt
// fixture-aux-file: aux/lang/WReport.kt
// fixture-aux-file: aux/lang/WReportReplay.kt
// fixture-aux-file: aux/lang/WReportStore.kt
// expect-clean
