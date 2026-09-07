package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.FixtureLoader
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.harness.assertMatchesExpectations
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Path

open class WrasseFixtureSpec : BaseSpec({
    val fixturesDir = Path.of(
        System.getProperty("wrasse.fixtures.dir") ?: error("System property 'wrasse.fixtures.dir' not set"),
    )
    val fixtures = FixtureLoader.load(fixturesDir)

    for (fixture in fixtures) {
        should("handle spec - ${fixture.ruleId} -> ${fixture.fixtureId}") {
            useTempDir { workDir ->
                useTempDir { fixOutputDir ->
                    val harness = WrasseTestHarness(
                        wrasseConfig = fixture.config,
                        warnOnly = fixture.warnOnly,
                        extraConfigFiles = fixture.extraConfigFiles,
                        fixOutputDir = fixOutputDir,
                    )
                    val source = TestSource("sample/test.kt", fixture.source)
                    val result = harness.compile(listOf(source) + fixture.auxSources, workDir)
                    result.assertMatchesExpectations(fixture)
                    val patchedContent = IdempotenceCycle.runIfFixEmitted(
                        harness,
                        workDir,
                        fixOutputDir,
                        source,
                        result,
                        fixture.auxSources,
                        fixture.multiPassFix,
                    )
                    val fixedSource = fixture.fixedSource
                    if (fixedSource != null) {
                        check(patchedContent != null) {
                            "Fixture ${fixture.ruleId}/${fixture.fixtureId} declares a .fixed.kt companion but " +
                                "its compile emitted no fix edits"
                        }
                        patchedContent shouldBe fixedSource
                    }
                }
            }
        }
    }
})
