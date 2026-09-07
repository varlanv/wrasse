package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class IsolatedProjectsSpec : ShouldSpec({

    should("lint and format a multi-module build with isolated projects on") {
        val playground = Playground.create("isolated")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.module("lib", mapOf("sample/Lib.kt" to "package sample\n\nval lib = 1;\n"))
            playground.settings("org.gradle.unsafe.isolated-projects=true\norg.gradle.configuration-cache.problems=fail\n")
            playground.config(WARN_CONFIG)

            val lint = playground.run("wrasseLint", "--parallel")
            lint.output shouldNotContain "problems were found"
            lint.output.countOf("wrasse: no-semicolons: Unnecessary semicolon") shouldBe 2

            val format = playground.run("wrasseFormat", "--parallel")
            format.output shouldNotContain "problems were found"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
            Files.readString(playground.dir.resolve("lib/src/main/kotlin/sample/Lib.kt")) shouldBe "package sample\n\nval lib = 1\n"

            val reused = playground.run("wrasseLint", "--parallel")
            reused.output shouldContain "Reusing configuration cache."
            reused.output shouldContain "BUILD SUCCESSFUL"
            reused.output shouldNotContain "no-semicolons"
        } finally {
            playground.delete()
        }
    }
})
