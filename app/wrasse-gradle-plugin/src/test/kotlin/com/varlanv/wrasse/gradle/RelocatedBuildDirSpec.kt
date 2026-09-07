package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class RelocatedBuildDirSpec : ShouldSpec({

    should("write the patch under a relocated build directory and still replay from it") {
        val playground = Playground.create("relocated-build-dir")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to SEMICOLON_SOURCE),
                afterExtension = """layout.buildDirectory.set(layout.projectDirectory.dir("out"))""",
            )
            playground.settings()
            playground.config(WARN_CONFIG)
            val finding = "w: ${playground.sourceUri("app", "sample/Sample.kt")}:3:16 " +
                "wrasse: no-semicolons: Unnecessary semicolon"

            val lint = playground.run("wrasseLint")

            lint.output.countOf(finding) shouldBe 1
            Files.readString(playground.dir.resolve("app/out/wrasse/main/patch/wrasse-fixes.txt")) shouldContain "Sample.kt"
            Files.exists(playground.dir.resolve("app/build")) shouldBe false

            val format = playground.run("wrasseFormat")
            format.output shouldContain "Fixed: "
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }
})
