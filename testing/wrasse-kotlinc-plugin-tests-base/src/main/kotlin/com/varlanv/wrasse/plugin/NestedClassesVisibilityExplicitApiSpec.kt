package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.ExitCode

/**
 * `nested-classes-visibility` must self-disable entirely under Kotlin's explicit API mode, the
 * same gate [com.varlanv.wrasse.rules.ModifierEngine] applies to `redundant-visibility-modifier`
 * (see [RedundantVisibilityModifierExplicitApiSpec]): an explicit `public` on the nested class is
 * *required* there, not misleading. Confirms the same source is flagged with the mode off (proving
 * the rule is genuinely wired and would otherwise fire on this shape) and silent under both
 * `-Xexplicit-api=strict` and `-Xexplicit-api=warning`, with the real compile still succeeding
 * either way.
 */
open class NestedClassesVisibilityExplicitApiSpec : BaseSpec({

    val wrasseConfig = """{"rules":{"nested-classes-visibility":{"level":"error"}}}"""
    val source = TestSource(
        "sample/Sample.kt",
        """
            package sample

            internal class Outer {
                public class Nested
            }
            """
            .trimIndent(),
    )

    should("flag a public nested class inside an internal class when explicit API mode is off") {
        useTempDir { workDir ->
            val harness = WrasseTestHarness(wrasseConfig = wrasseConfig)
            val result = harness.compile(listOf(source), workDir)

            result.wrasseDiagnostics shouldHaveSize 1
        }
    }

    should("stay silent under -Xexplicit-api=strict") {
        useTempDir { workDir ->
            val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, explicitApiMode = "strict")
            val result = harness.compile(listOf(source), workDir)

            result.wrasseDiagnostics.shouldBeEmpty()
            result.exitCode shouldBe ExitCode.OK
        }
    }

    should("stay silent under -Xexplicit-api=warning") {
        useTempDir { workDir ->
            val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, explicitApiMode = "warning")
            val result = harness.compile(listOf(source), workDir)

            result.wrasseDiagnostics.shouldBeEmpty()
            result.exitCode shouldBe ExitCode.OK
        }
    }
})
