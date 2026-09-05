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
 * The hard-call safety net for `redundant-visibility-modifier` (autoformat-scope.md's hard call
 * #4): an explicit `public` is a *required* declaration under Kotlin's explicit API mode, not
 * redundant, so the rule must self-disable entirely there rather than risk emitting a fix that
 * breaks an explicit-API build. Confirms the same source is flagged with the mode off (proving the
 * rule is genuinely wired and would otherwise fire on this shape) and silent under both
 * `-Xexplicit-api=strict` and `-Xexplicit-api=warning`, with the real compile still succeeding
 * either way — see [com.varlanv.wrasse.rules.ModifierEngine]'s KDoc and
 * `WrasseRuleConfig.explicitApiActive`.
 */
open class RedundantVisibilityModifierExplicitApiSpec : BaseSpec({

    val wrasseConfig = """{"rules":{"redundant-visibility-modifier":{"level":"error"}}}"""
    val source = TestSource(
        "sample/Sample.kt",
        """
            package sample

            public class Foo {
                public fun bar(): Int = 1
            }
            """
            .trimIndent(),
    )

    should("flag a redundant public modifier when explicit API mode is off") {
        useTempDir { workDir ->
            val harness = WrasseTestHarness(wrasseConfig = wrasseConfig)
            val result = harness.compile(listOf(source), workDir)

            result.wrasseDiagnostics shouldHaveSize 2
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
