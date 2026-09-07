package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import org.gradle.testkit.runner.GradleRunner

class ExcludedGeneratedSourceSpec : ShouldSpec({

    should("skip a hand-made file under the build directory for both lint and format, while a normal source is still checked") {
        val playground = Playground.create("excluded-generated")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to "package sample\n\nimport generated.GENERATED\n\nval answer = GENERATED;\n"),
                afterExtension = """
                kotlin {
                    sourceSets {
                        main {
                            kotlin.srcDir("build/generated/src/main/kotlin")
                        }
                    }
                }
                """.trimIndent(),
            )
            val generatedContent = "package generated\n\nconst val GENERATED = 1;\n"
            val generatedPath = "build/generated/src/main/kotlin/generated/Generated.kt"
            playground.rawFile("app", generatedPath, generatedContent)
            playground.settings()
            playground.config(WARN_CONFIG)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")

            val lint = playground.run("wrasseLint")
            lint.output shouldContain "BUILD SUCCESSFUL"
            lint.output shouldContain "w: $sourceUri:5:23 wrasse: no-semicolons: Unnecessary semicolon"
            lint.output shouldNotContain "Generated.kt"

            val format = playground.run("wrasseFormat")
            format.output shouldContain "Fixed: "
            format.output shouldNotContain "Generated.kt"
            Files.readString(playground.dir.resolve("app").resolve(generatedPath)) shouldBe generatedContent
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe
                "package sample\n\nimport generated.GENERATED\n\nval answer = GENERATED\n"
        } finally {
            playground.delete()
        }
    }

    should("skip a hand-made file under the build directory when the project is reached through a symlink") {
        val playground = Playground.create("excluded-generated-symlink")
        val linkParent = Files.createTempDirectory("wrasse-symlink-parent-")
        val link = linkParent.resolve("linked")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to "package sample\n\nimport generated.GENERATED\n\nval answer = GENERATED;\n"),
                afterExtension = """
                kotlin {
                    sourceSets {
                        main {
                            kotlin.srcDir("build/generated/src/main/kotlin")
                        }
                    }
                }
                """.trimIndent(),
            )
            val generatedContent = "package generated\n\nconst val GENERATED = 1;\n"
            val generatedPath = "build/generated/src/main/kotlin/generated/Generated.kt"
            playground.rawFile("app", generatedPath, generatedContent)
            playground.settings()
            playground.config(WARN_CONFIG)
            Files.createSymbolicLink(link, playground.dir)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")

            val lint = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(link.toFile())
                .withArguments("wrasseLint", "--stacktrace")
                .build()

            lint.output shouldContain "BUILD SUCCESSFUL"
            lint.output shouldContain "w: $sourceUri:5:23 wrasse: no-semicolons: Unnecessary semicolon"
            lint.output shouldNotContain "Generated.kt"
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(linkParent)
            playground.delete()
        }
    }
})
