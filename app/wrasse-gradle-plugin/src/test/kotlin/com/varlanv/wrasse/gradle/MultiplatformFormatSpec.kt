package com.varlanv.wrasse.gradle

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import org.gradle.testkit.runner.TaskOutcome

class MultiplatformFormatSpec : ShouldSpec({

    should("keep a format run quiet when kapt stubs compile before the JVM target") {
        val playground = Playground.create("multiplatform-kapt-format")
        try {
            playground.rawModule(
                "app",
                """
                plugins {
                    kotlin("multiplatform") version "$KOTLIN_VERSION"
                    kotlin("kapt") version "$KOTLIN_VERSION"
                    id("com.google.devtools.ksp") version "2.3.10"
                    id("com.varlanv.wrasse") apply false
                }

                kotlin {
                    jvm()
                    js { nodejs() }
                }

                afterEvaluate {
                    pluginManager.apply("com.varlanv.wrasse")
                }
                """.trimIndent(),
            )
            playground.source("app", "sample/Common.kt", "package sample\n\nfun common()  =  1\n", "src/commonMain/kotlin")
            playground.source("app", "sample/Jvm.kt", "package sample\n\nfun jvm()  =  2\n", "src/jvmMain/kotlin")
            playground.settings()
            playground.config("""{"format":{"enabled":true}}""")

            val result = playground.run("wrasseFormat")
            result.output shouldContain "BUILD SUCCESSFUL"
            result.task(":app:kaptGenerateStubsKotlinJvm")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldNotContain "wrasse: format:"
            result.output shouldNotContain "wrasse: named-arguments:"
            Files.readString(playground.dir.resolve("app/src/jvmMain/kotlin/sample/Jvm.kt")) shouldBe
                "package sample\n\nfun jvm() = 2\n"
        } finally {
            playground.delete()
        }
    }

    should("format a multiplatform module after clean when its coordinates match a published dependency") {
        val playground = Playground.create("multiplatform-self-dependency")
        try {
            playground.rawModule(
                "lang",
                """
                plugins {
                    kotlin("multiplatform") version "$KOTLIN_VERSION"
                    `maven-publish`
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
            playground.source("lang", "sample/Common.kt", "package sample\n\nfun common()  =  1\n", "src/commonMain/kotlin")
            playground.source("lang", "sample/Jvm.kt", "package sample\n\nfun jvm()  =  2\n", "src/jvmMain/kotlin")
            playground.settings("group=com.varlanv.koper\nversion=0.0.1-SNAPSHOT\n")
            playground.config("""{"format":{"enabled":true}}""")

            playground.run("clean")
            val result = playground.run("format")
            result.output shouldContain "BUILD SUCCESSFUL"
            result.output shouldNotContain "Plugin classpath entry points to a non-existent location"
            Files.readString(playground.dir.resolve("lang/src/jvmMain/kotlin/sample/Jvm.kt")) shouldBe
                "package sample\n\nfun jvm() = 2\n"
        } finally {
            playground.delete()
        }
    }

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
