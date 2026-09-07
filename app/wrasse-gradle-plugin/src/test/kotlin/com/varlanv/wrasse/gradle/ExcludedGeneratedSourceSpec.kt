package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class ExcludedGeneratedSourceSpec : ShouldSpec({

    should("skip a hand-made file under the build directory for both lint and format, while a normal source is still checked") {
        val playground = Playground.create("excluded-generated")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to SEMICOLON_SOURCE),
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
            val generatedContent = "package generated\n\nval generated = 1;\n"
            val generatedPath = "build/generated/src/main/kotlin/generated/Generated.kt"
            playground.rawFile("app", generatedPath, generatedContent)
            playground.settings()
            playground.config(WARN_CONFIG)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")

            val lint = playground.run("wrasseLint")
            lint.output shouldContain "w: $sourceUri:3:16 wrasse: no-semicolons: Unnecessary semicolon"
            lint.output shouldNotContain "Generated.kt"

            val format = playground.run("wrasseFormat")
            format.output shouldNotContain "Generated.kt"
            Files.readString(playground.dir.resolve("app").resolve(generatedPath)) shouldBe generatedContent
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }
})
