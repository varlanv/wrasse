package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.FixtureParser
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import com.varlanv.wrasse.testing.harness.assertMatchesExpectations

class NoWildcardImportsHarnessSpec :
    BaseSpec({
        val config = """
            {
                "exclude": [],
                "rules": {
                    "no-semicolons": { "enabled": false, "severity": "warning", "exclude": [] },
                    "no-wildcard-imports": { "enabled": true, "severity": "error", "exclude": [] },
                    "trailing-newline": { "enabled": false, "severity": "warning", "exclude": [] }
                }
            }
        """.trimIndent()

        val harness = WrasseTestHarness(config)

        fun loadFixture(name: String): String =
            NoWildcardImportsHarnessSpec::class.java
                .getResource("/fixtures/no-wildcard-imports/$name")!!
                .readText()

        should("flag wildcard imports") {
            val source = loadFixture("wildcard-flagged.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource)))
            result.assertMatchesExpectations(fixture)
        }

        should("pass explicit imports") {
            val source = loadFixture("explicit-ok.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource)))
            result.assertMatchesExpectations(fixture)
        }
    })
