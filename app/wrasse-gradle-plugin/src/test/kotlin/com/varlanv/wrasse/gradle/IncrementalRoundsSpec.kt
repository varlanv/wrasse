package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class IncrementalRoundsSpec : ShouldSpec({

    should("keep every compile round of a format run quiet, not only the first") {
        val playground = Playground.create("format-rounds")
        try {
            playground.module(
                "app",
                mapOf(
                    "sample/Api.kt" to "package sample\n\nclass Api {\n    fun one(): Int = 1\n}\n",
                    "sample/Client.kt" to "package sample\n\nfun use(api: Api): Int  =  api.one()\n",
                ),
                extension = "warnOnly.set(true)",
            )
            playground.settings()
            playground.config("""{"format":{"enabled":true}}""")
            val clientUri = playground.sourceUri("app", "sample/Client.kt")

            val baseline = playground.run("compileKotlin")
            baseline.output shouldContain "w: $clientUri:1:1 wrasse: format: File is not wrasse-formatted"

            playground.source(
                "app",
                "sample/Api.kt",
                "package sample\n\nclass Api {\n    fun one(): Int = 1\n\n    fun two(): Int  =  2\n}\n",
            )
            val format = playground.run("wrasseFormat")
            format.output shouldNotContain "File is not wrasse-formatted"
            format.output shouldContain "Fixed: "
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Client.kt")) shouldBe
                "package sample\n\nfun use(api: Api): Int = api.one()\n"
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Api.kt")) shouldBe
                "package sample\n\nclass Api {\n    fun one(): Int = 1\n\n    fun two(): Int = 2\n}\n"
        } finally {
            playground.delete()
        }
    }
})
