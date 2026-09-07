package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain

class OutOfProjectSourceSpec : ShouldSpec({

    should("fail wrasseLint on a finding from a srcDir outside the project directory") {
        val playground = Playground.create("outside-srcdir")
        try {
            playground.module(
                "app",
                mapOf("sample/Sample.kt" to "package sample\n\nval other = 1\n"),
                afterExtension = """
                kotlin {
                    sourceSets {
                        main {
                            kotlin.srcDir("../shared/src")
                        }
                    }
                }
                """.trimIndent(),
            )
            val sharedFile = playground.rootFile("shared/src/Extra.kt", SEMICOLON_SOURCE)
            playground.settings()
            playground.config(ERROR_CONFIG)
            val finding = "e: ${sharedFile.toUri()}:3:16 wrasse: no-semicolons: Unnecessary semicolon"

            val lint = playground.runAndFail("wrasseLint")

            lint.output shouldContain finding
        } finally {
            playground.delete()
        }
    }
})
