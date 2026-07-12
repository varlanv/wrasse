package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.FixtureLoader
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.harness.assertMatchesExpectations
import java.nio.file.Path

open class WrasseFixtureSpec : BaseSpec({
    val fixturesDir = Path.of(
        System.getProperty("wrasse.fixtures.dir")
            ?: error("System property 'wrasse.fixtures.dir' not set")
    )
    val fixtures = FixtureLoader.load(fixturesDir)

    for (fixture in fixtures) {
        should("handle spec - ${fixture.ruleId} -> ${fixture.fixtureId}") {
            val harness = WrasseTestHarness(
                wrasseConfig = fixture.config,
                warnOnly = fixture.warnOnly,
                extraConfigFiles = fixture.extraConfigFiles
            )
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.source)))
            result.assertMatchesExpectations(fixture)
        }
    }
})
