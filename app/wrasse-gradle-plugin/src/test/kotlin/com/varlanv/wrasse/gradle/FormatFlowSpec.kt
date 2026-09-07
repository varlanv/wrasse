package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class FormatFlowSpec : ShouldSpec({

    val config = """{"rules":{"no-semicolons":{"level":"error"},"magic-number":{"level":"warn"}}}"""
    val source = "package sample\n\nfun main() {\n    println(42);\n}\n"

    should("rewrite the source, keep the fixed finding quiet, replay what remains, and be a no-op the second time") {
        val playground = Playground.create("format")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to source))
            playground.settings()
            playground.config(config)
            val sourceUri = playground.sourceUri("app", "sample/Sample.kt")

            val first = playground.run("wrasseFormat")
            first.output shouldNotContain "no-semicolons"
            first.output shouldContain "Fixed: "
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe
                "package sample\n\nfun main() {\n    println(42)\n}\n"
            Files.exists(playground.dir.resolve("app/build/wrasse/main/format-request")) shouldBe false

            val second = playground.run("wrasseFormat")
            second.output shouldNotContain "Fixed: "
            second.output.countOf("w: $sourceUri:4:13 wrasse: magic-number: This expression contains a magic number; consider defining it as a well-named constant") shouldBe 1
            second.output shouldContain "BUILD SUCCESSFUL"
        } finally {
            playground.delete()
        }
    }

    should("apply nothing and leave no request behind when the compile was up to date") {
        val playground = Playground.create("format-uptodate")
        try {
            playground.module("app", mapOf("sample/Sample.kt" to CLEAN_SOURCE))
            playground.settings()
            playground.config(ERROR_CONFIG)

            playground.run("wrasseFormat")
            val again = playground.run("wrasseFormat")
            again.output shouldContain "Task :app:compileKotlin UP-TO-DATE"
            Files.exists(playground.dir.resolve("app/build/wrasse/main/format-request")) shouldBe false
            Files.readString(playground.dir.resolve("app/src/main/kotlin/sample/Sample.kt")) shouldBe CLEAN_SOURCE
        } finally {
            playground.delete()
        }
    }
})
