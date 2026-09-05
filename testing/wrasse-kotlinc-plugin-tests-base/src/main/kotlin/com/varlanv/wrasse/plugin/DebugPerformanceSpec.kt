package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FormatRequest
import com.varlanv.wrasse.lang.PerfStore
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/**
 * A request carrying `debugPerformance=true` makes the compile record per-phase and per-rule
 * timings into `<fixOutputDir>/wrasse-perf.txt` without changing what is reported; without the
 * request nothing is written.
 */
open class DebugPerformanceSpec : BaseSpec({

    val wrasseConfig = """{"format":{"enabled":true},"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"error"}}}"""
    val source = TestSource(
        "sample/Sample.kt",
        """
            package sample

            fun main() {
                println(42);
            }
            """
            .trimIndent(),
    )

    should("record phases and rules into the perf file when the request asks for it") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                FormatRequest.write(fixOutputDir, formatting = false, debugPerformance = true)
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val result = harness.compile(listOf(source), workDir)

                result.wrasseDiagnostics.size shouldBe 3
                val reports = PerfStore.collect(fixOutputDir)
                reports.map { it.first } shouldBe listOf("compile ${fixOutputDir.fileName}")
                val recorder = reports[0].second
                recorder
                    .entries()
                    .map { it.key } shouldContainAll
                    listOf(
                        "phase:rule-init",
                        "phase:walk",
                        "phase:format-finish",
                        "phase:format-splice",
                        "phase:format-render",
                        "phase:edit-plan",
                        "phase:patch-store",
                        "phase:total",
                        "rule:no-semicolons",
                        "rule:magic-number",
                        "rule:format",
                        "count:files",
                        "count:reports",
                        "count:edits",
                        "count:bytes",
                        "count:lines",
                    )
                recorder["count:files"]!!.total shouldBe 1
                recorder["count:reports"]!!.total shouldBe 3
                recorder["count:lines"]!!.total shouldBe 4
                recorder.entries().any { it.key.startsWith("file:") && it.key.endsWith("Sample.kt") } shouldBe true
                Files.exists(fixOutputDir.resolve(FormatRequest.FILE_NAME)) shouldBe false
            }
        }
    }

    should("write nothing without the request") {
        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                harness.compile(listOf(source), workDir)
                Files.exists(fixOutputDir.resolve(PerfStore.FILE_NAME)) shouldBe false
            }
        }
    }
})
