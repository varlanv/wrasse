package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * Real-compile safety net for the semicolon standing in for a `for`/`while`'s empty
 * [com.varlanv.wrasse.model.WNodeType.BODY] or an `if`'s empty
 * [com.varlanv.wrasse.model.WNodeType.THEN]: kotlinc's grammar requires that semicolon, so
 * `no-semicolons` must never flag it — see
 * [com.varlanv.wrasse.rules.NoSemicolonsRule]'s KDoc for the invariant.
 */
open class NoSemicolonsBareConstructBodySafetySpec : BaseSpec({

    val wrasseConfig = """{"rules":{"no-semicolons":{"level":"error"}}}"""

    should("leave a for-loop's semicolon-only empty body untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun f(xs: IntArray) {
                for (i in xs);
            }
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics.shouldBeEmpty()
                IdempotenceCycle.assertNoResidualEdits(fixOutputDir.resolve("wrasse-fixes.txt"))
            }
        }
    }

    should("leave a while-loop's semicolon-only empty body untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun f() {
                while (System.currentTimeMillis() < 0);
            }
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics.shouldBeEmpty()
                IdempotenceCycle.assertNoResidualEdits(fixOutputDir.resolve("wrasse-fixes.txt"))
            }
        }
    }

    should("leave an if's semicolon-only empty then-branch untouched and still compiling") {
        val source = TestSource(
            "sample/Sample.kt",
            """
            package sample

            fun f(flag: Boolean) {
                if (flag);
            }
            """.trimIndent(),
        )

        useTempDir { workDir ->
            useTempDir { fixOutputDir ->
                val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, fixOutputDir = fixOutputDir)
                val round1 = harness.compile(listOf(source), workDir)
                round1.wrasseDiagnostics.shouldBeEmpty()
                IdempotenceCycle.assertNoResidualEdits(fixOutputDir.resolve("wrasse-fixes.txt"))
            }
        }
    }
})
