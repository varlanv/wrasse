package com.varlanv.wrasse.testing.harness

import com.varlanv.wrasse.testing.BaseSpec

class NoSemicolonsHarnessSpec :
    BaseSpec({
        val config = """
            {
                "exclude": [],
                "rules": {
                    "no-semicolons": {
                        "enabled": true,
                        "severity": "error",
                        "exclude": []
                    }
                }
            }
        """.trimIndent()

        val harness = WrasseTestHarness(config)

        fun loadFixture(name: String): String =
            NoSemicolonsHarnessSpec::class.java
                .getResource("/fixtures/no-semicolons/$name")!!
                .readText()

        should("flag unnecessary trailing semicolon") {
            val source = loadFixture("unnecessary-trailing.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource)))
            result.assertMatchesExpectations(fixture)
        }

        should("pass clean code without semicolons") {
            val source = loadFixture("clean.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource)))
            result.assertMatchesExpectations(fixture)
        }

        should("allow semicolons in for loops") {
            val source = loadFixture("for-loop-ok.kt")
            val fixture = FixtureParser.parse(source)
            val result = harness.compile(listOf(TestSource("sample/test.kt", fixture.strippedSource)))
            result.assertMatchesExpectations(fixture)
        }
    })
