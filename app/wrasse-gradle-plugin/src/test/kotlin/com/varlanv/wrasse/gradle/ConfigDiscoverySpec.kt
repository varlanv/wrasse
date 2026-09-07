package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain

class ConfigDiscoverySpec : ShouldSpec({

    should("fail at configuration time when no config file is found") {
        val playground = Playground.create("no-config")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings()

            val result = playground.runAndFail("wrasseLint")

            result.output shouldContain
                "wrasse: no wrasse.json or wrasse.jsonc found walking up from ${playground.dir.resolve("app")}"
        } finally {
            playground.delete()
        }
    }

    should("fail at configuration time when the project path contains a comma") {
        val playground = Playground.create("comma,path")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to SEMICOLON_SOURCE))
            playground.settings()
            playground.config(WARN_CONFIG)

            val result = playground.runAndFail("wrasseLint")

            result.output shouldContain
                "wrasse: the Kotlin compiler splits plugin options on ',', so wrasse cannot run from a path " +
                "containing one: ${playground.dir.resolve("app")}"
        } finally {
            playground.delete()
        }
    }
})
