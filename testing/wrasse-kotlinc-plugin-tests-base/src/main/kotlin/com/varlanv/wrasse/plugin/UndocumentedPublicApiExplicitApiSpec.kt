package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize

/**
 * `undocumented-public-class`/`undocumented-public-function`/`undocumented-public-property`
 * (see [com.varlanv.wrasse.rules.KdocEngine]) must self-disable entirely unless the compile runs
 * under Kotlin's explicit API mode — requiring KDoc on every public declaration in ordinary code
 * would fight this project's own "KDoc is contract-only, not mandatory" style. Confirms the same
 * source is flagged under `-Xexplicit-api=strict` and silent with the mode off, that a documented
 * declaration and a member (non-top-level) declaration are never candidates, and that
 * `kdoc-tag-mismatch` (never gated on explicit API mode) fires either way.
 */
open class UndocumentedPublicApiExplicitApiSpec :
    BaseSpec(
        {

            val wrasseConfig =
            """{"rules":{"undocumented-public-class":{"level":"error"},"undocumented-public-function":{"level":"error"},"undocumented-public-property":{"level":"error"}}}"""
            val source = TestSource(
                "sample/Sample.kt",
                """
                    package sample

                    public class Foo

                    public fun bar(): Int = 1

                    public val baz: Int = 1
                    """
                    .trimIndent(),
            )

            should("stay silent when explicit API mode is off") {
                useTempDir { workDir ->
                    val harness = WrasseTestHarness(wrasseConfig = wrasseConfig)
                    val result = harness.compile(listOf(source), workDir)

                    result.wrasseDiagnostics.shouldBeEmpty()
                }
            }

            should("flag every undocumented public declaration under -Xexplicit-api=strict") {
                useTempDir { workDir ->
                    val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, explicitApiMode = "strict")
                    val result = harness.compile(listOf(source), workDir)

                    result.wrasseDiagnostics shouldHaveSize 3
                }
            }

            should("stay silent for a documented top-level declaration even under -Xexplicit-api=strict") {
                val documented = TestSource(
                    "sample/Documented.kt",
                    """
                        package sample

                        /** Does a thing. */
                        public class Foo

                        /** Does a thing. */
                        public fun bar(): Int = 1

                        /** Does a thing. */
                        public val baz: Int = 1
                        """
                        .trimIndent(),
                )
                useTempDir { workDir ->
                    val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, explicitApiMode = "strict")
                    val result = harness.compile(listOf(documented), workDir)

                    result.wrasseDiagnostics.shouldBeEmpty()
                }
            }

            should("stay silent for a member (non-top-level) declaration even under -Xexplicit-api=strict") {
                val member = TestSource(
                    "sample/Member.kt",
                    """
                        package sample

                        /** A documented holder so only its members are candidates here. */
                        public class Foo {
                            public fun bar(): Int = 1
                            public val baz: Int = 1
                        }
                        """
                        .trimIndent(),
                )
                useTempDir { workDir ->
                    val harness = WrasseTestHarness(wrasseConfig = wrasseConfig, explicitApiMode = "strict")
                    val result = harness.compile(listOf(member), workDir)

                    result.wrasseDiagnostics.shouldBeEmpty()
                }
            }

            val mismatchConfig = """{"rules":{"kdoc-tag-mismatch":{"level":"error"}}}"""
            val mismatchSource = TestSource(
                "sample/Mismatch.kt",
                """
                    package sample

                    /**
                     * @param wrong description
                     */
                    public fun foo(actual: Int): Unit {
                        println(actual)
                    }
                    """
                    .trimIndent(),
            )

            should("still flag kdoc-tag-mismatch with explicit API mode off") {
                useTempDir { workDir ->
                    val harness = WrasseTestHarness(wrasseConfig = mismatchConfig)
                    val result = harness.compile(listOf(mismatchSource), workDir)

                    result.wrasseDiagnostics shouldHaveSize 1
                }
            }

            should("still flag kdoc-tag-mismatch under -Xexplicit-api=strict") {
                useTempDir { workDir ->
                    val harness = WrasseTestHarness(wrasseConfig = mismatchConfig, explicitApiMode = "strict")
                    val result = harness.compile(listOf(mismatchSource), workDir)

                    result.wrasseDiagnostics shouldHaveSize 1
                }
            }
        },
    )
