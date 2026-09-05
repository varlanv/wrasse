package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.FixtureLoader
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

private const val BASE_CONFIG = """{"rules":{}}"""

private fun writeFile(path: Path, content: String) {
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
}

class FixtureLoaderSpec : BaseSpec({

    should("wire a fixture-aux-file directive to an aux source under sample/") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // fixture-aux-file: aux/Helper.kt
                    // expect-clean
                    """
                    .trimIndent(),
            )
            writeFile(fixturesDir.resolve("sample-rule/aux/Helper.kt"), "package sample.aux\n\nclass Helper\n")

            val fixtures = FixtureLoader.load(fixturesDir)

            fixtures shouldHaveSize 1
            fixtures[0].auxSources shouldHaveSize 1
            fixtures[0].auxSources[0].path shouldBe "sample/aux/Helper.kt"
            fixtures[0].auxSources[0].content shouldBe "package sample.aux\n\nclass Helper\n"
        }
    }

    should("never discover a .kt file under aux/ as its own fixture") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // fixture-aux-file: aux/Helper.kt
                    // expect-clean
                    """
                    .trimIndent(),
            )
            writeFile(fixturesDir.resolve("sample-rule/aux/Helper.kt"), "package sample.aux\n\nclass Helper\n")

            val fixtures = FixtureLoader.load(fixturesDir)

            fixtures.map { it.fixtureId } shouldBe listOf("main")
        }
    }

    should("fail with the full message when a fixture-aux-file directive names a file that does not exist") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // fixture-aux-file: aux/Missing.kt
                    // expect-clean
                    """
                    .trimIndent(),
            )

            val ruleDir = fixturesDir.resolve("sample-rule")
            shouldThrow<IllegalArgumentException> {
                FixtureLoader.load(fixturesDir)
            }
                .message shouldBe
                "Fixture main declares fixture-aux-file 'aux/Missing.kt' but ${ruleDir.resolve(
                    "aux/Missing.kt",
                )} does not exist"
        }
    }

    should("load a fixture with no fixture-aux-file directives with an empty auxSources list") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // expect-clean
                    """
                    .trimIndent(),
            )

            val fixtures = FixtureLoader.load(fixturesDir)

            fixtures shouldHaveSize 1
            fixtures[0].auxSources shouldBe emptyList()
        }
    }

    should("never discover a .fixed.kt companion file as its own fixture") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // expect-clean
                    """
                    .trimIndent(),
            )
            writeFile(fixturesDir.resolve("sample-rule/main.fixed.kt"), "package sample\n")

            val fixtures = FixtureLoader.load(fixturesDir)

            fixtures.map { it.fixtureId } shouldBe listOf("main")
        }
    }

    should("attach a .fixed.kt companion's content to the matching fixture") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // expect-clean
                    """
                    .trimIndent(),
            )
            writeFile(fixturesDir.resolve("sample-rule/main.fixed.kt"), "package sample\n")

            val fixtures = FixtureLoader.load(fixturesDir)

            fixtures shouldHaveSize 1
            fixtures[0].fixedSource shouldBe "package sample\n"
        }
    }

    should("leave fixedSource null for a fixture with no .fixed.kt companion") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // expect-clean
                    """
                    .trimIndent(),
            )

            val fixtures = FixtureLoader.load(fixturesDir)

            fixtures shouldHaveSize 1
            fixtures[0].fixedSource shouldBe null
        }
    }

    should("fail with the full message when a .fixed.kt companion names no matching fixture") {
        useTempDir { fixturesDir ->
            writeFile(fixturesDir.resolve("wrasse.json"), BASE_CONFIG)
            writeFile(
                fixturesDir.resolve("sample-rule/main.kt"),
                """
                    package sample
                    // expect-clean
                    """
                    .trimIndent(),
            )
            writeFile(fixturesDir.resolve("sample-rule/typo.fixed.kt"), "package sample\n")

            val ruleDir = fixturesDir.resolve("sample-rule")
            shouldThrow<IllegalArgumentException> {
                FixtureLoader.load(fixturesDir)
            }.message shouldBe "Companion file 'typo.fixed.kt' in $ruleDir has no matching fixture 'typo.kt'"
        }
    }
})
