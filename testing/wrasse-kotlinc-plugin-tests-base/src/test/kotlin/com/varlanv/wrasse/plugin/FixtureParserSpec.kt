package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.ExpectedSeverity
import com.varlanv.wrasse.testing.harness.FixtureParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class FixtureParserSpec :
    BaseSpec({
        should("parse expect-clean directive") {
            val fixture = FixtureParser.parse(
                """
                package sample
                val x = 1
                // expect-clean
                """.trimIndent()
            )
            fixture.expectClean shouldBe true
            fixture.expectations.shouldBeEmpty()
            fixture.strippedSource shouldBe "package sample\nval x = 1"
        }

        should("parse expect-error directive") {
            val fixture = FixtureParser.parse(
                """
                package sample
                val x = 1;
                // expect-error 2:10 no-semicolons "Unnecessary semicolon"
                """.trimIndent()
            )
            fixture.expectClean shouldBe false
            fixture.expectations shouldHaveSize 1
            fixture.expectations[0].severity shouldBe ExpectedSeverity.ERROR
            fixture.expectations[0].line shouldBe 2
            fixture.expectations[0].column shouldBe 10
            fixture.expectations[0].ruleId shouldBe "no-semicolons"
            fixture.expectations[0].message shouldBe "Unnecessary semicolon"
            fixture.strippedSource shouldBe "package sample\nval x = 1;"
        }

        should("parse expect-warning directive") {
            val fixture = FixtureParser.parse(
                """
                package sample
                val x = 1;
                // expect-warning 2:10 no-semicolons "Unnecessary semicolon"
                """.trimIndent()
            )
            fixture.expectClean shouldBe false
            fixture.expectations shouldHaveSize 1
            fixture.expectations[0].severity shouldBe ExpectedSeverity.WARNING
            fixture.expectations[0].line shouldBe 2
            fixture.expectations[0].column shouldBe 10
            fixture.expectations[0].ruleId shouldBe "no-semicolons"
            fixture.expectations[0].message shouldBe "Unnecessary semicolon"
        }

        should("parse multiple expect-error directives") {
            val fixture = FixtureParser.parse(
                """
                package sample
                val x = 1;
                val y = 2;
                // expect-error 2:10 no-semicolons "Unnecessary semicolon"
                // expect-error 3:10 no-semicolons "Unnecessary semicolon"
                """.trimIndent()
            )
            fixture.expectations shouldHaveSize 2
            fixture.expectations[0].line shouldBe 2
            fixture.expectations[1].line shouldBe 3
        }

        should("fail on fixture with no directives") {
            shouldThrow<IllegalArgumentException> {
                FixtureParser.parse("package sample\nval x = 1")
            }.message shouldBe "Fixture must have at least one // expect-error, // expect-warning, or // expect-clean directive"
        }

        should("fail on fixture with both expect-clean and expect-error") {
            shouldThrow<IllegalArgumentException> {
                FixtureParser.parse(
                    """
                    package sample
                    val x = 1;
                    // expect-clean
                    // expect-error 2:10 no-semicolons "Unnecessary semicolon"
                    """.trimIndent()
                )
            }.message shouldBe "Fixture cannot have both // expect-clean and // expect-error/expect-warning directives"
        }

        should("strip trailing blank lines from source") {
            val fixture = FixtureParser.parse(
                """
                package sample
                val x = 1

                // expect-clean
                """.trimIndent()
            )
            fixture.strippedSource shouldBe "package sample\nval x = 1"
        }
    })
