package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class MultiplatformFormatSpec : ShouldSpec({

    should("format Kotlin multiplatform sources without reporting formatting violations") {
        val playground = Playground.create("multiplatform-format")
        try {
            playground.rawModule(
                "app",
                """
                plugins {
                    kotlin("multiplatform") version "$KOTLIN_VERSION"
                    id("com.varlanv.wrasse")
                }

                kotlin {
                    jvm()
                    js { nodejs() }
                }

                tasks.register("format") {
                    dependsOn("wrasseFormat")
                }
                """.trimIndent(),
            )
            playground.source("app", "sample/Common.kt", "package sample\n\nfun common()  =  1\n", "src/commonMain/kotlin")
            playground.source("app", "sample/Jvm.kt", "package sample\n\nfun jvm()  =  2\n", "src/jvmMain/kotlin")
            playground.source("app", "sample/Js.kt", "package sample\n\nfun js()  =  3\n", "src/jsMain/kotlin")
            playground.settings()
            playground.config("""{"format":{"enabled":true}}""")

            val result = playground.run("format")
            result.output shouldContain "BUILD SUCCESSFUL"
            result.output shouldNotContain "File is not wrasse-formatted"
            result.output shouldContain "> Task :app:compileKotlinJs"
            result.output shouldContain "> Task :app:compileCommonMainKotlinMetadata"
            Files.readString(playground.dir.resolve("app/src/jvmMain/kotlin/sample/Jvm.kt")) shouldBe
                "package sample\n\nfun jvm() = 2\n"
            Files.readString(playground.dir.resolve("app/src/commonMain/kotlin/sample/Common.kt")) shouldBe
                "package sample\n\nfun common() = 1\n"
            Files.readString(playground.dir.resolve("app/src/jsMain/kotlin/sample/Js.kt")) shouldBe
                "package sample\n\nfun js() = 3\n"
        } finally {
            playground.delete()
        }
    }
})
