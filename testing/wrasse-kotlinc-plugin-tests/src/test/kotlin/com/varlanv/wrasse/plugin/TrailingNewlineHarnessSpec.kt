package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.FixtureParser
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.harness.assertMatchesExpectations

class TrailingNewlineHarnessSpec :
    BaseSpec({
        val config = """
            {
                "exclude": [],
                "rules": {
                    "no-semicolons": { "enabled": false, "severity": "warning", "exclude": [] },
                    "no-wildcard-imports": { "enabled": false, "severity": "warning", "exclude": [] },
                    "trailing-newline": { "enabled": true, "severity": "error", "exclude": [] }
                }
            }
        """.trimIndent()

        val harness = WrasseTestHarness(config)

        fun loadFixture(name: String): String =
            TrailingNewlineHarnessSpec::class.java
                .getResource("/fixtures/trailing-newline/$name")!!
                .readText()

        should("flag file without trailing newline") {
            val source = loadFixture("missing-newline.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource)))
            result.assertMatchesExpectations(fixture)
        }

        should("pass file with trailing newline") {
            val source = loadFixture("has-newline.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource + "\n")))
            result.assertMatchesExpectations(fixture)
        }
    })
