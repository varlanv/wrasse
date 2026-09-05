package com.varlanv.gradle.plugin

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * `wrasseFormatRequest` drops a `format-request` into every compilation's patch directory and
 * orders itself before the compiles, so the following check compile prints no autofixable
 * diagnostic while still emitting its patch; the request is consumed by that compile and any
 * leftover is removed by `wrasseApply`.
 */
private val catalogPath: String =
    (System.getProperty("wrasse.realRepoCatalogPath") ?: error("system property 'wrasse.realRepoCatalogPath' not set"))
        .replace("\\", "\\\\")

private fun writeFixtureProject(projectDir: Path): Path {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
            }
        }
        plugins {
            id("org.gradle.toolchains.foojay-resolver-convention")
        }
        dependencyResolutionManagement {
            versionCatalogs {
                create("libs") {
                    from(files("$catalogPath"))
                }
            }
        }
        include("module", "testing:common-test")
        """.trimIndent(),
    )
    Files.writeString(projectDir.resolve("build.gradle.kts"), "")
    Files.createDirectories(projectDir.resolve("testing/common-test"))
    Files.writeString(
        projectDir.resolve("testing/common-test/build.gradle.kts"),
        """
        plugins {
            id("com.varlanv.wrasse.gradle.internal-gradle-convention-plugin")
        }
        """.trimIndent(),
    )
    Files.writeString(projectDir.resolve("wrasse.json"), """{"rules":{"no-semicolons":{"level":"warn"}}}""")
    val moduleDir = Files.createDirectories(projectDir.resolve("module"))
    Files.writeString(
        moduleDir.resolve("build.gradle.kts"),
        """
        plugins {
            id("com.varlanv.wrasse.gradle.internal-gradle-convention-plugin")
        }

        afterEvaluate {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions {
                    allWarningsAsErrors.set(false)
                }
            }
        }
        """.trimIndent(),
    )
    val srcDir = Files.createDirectories(moduleDir.resolve("src/main/kotlin/sample"))
    Files.writeString(srcDir.resolve("Sample.kt"), "package sample\n\nval answer = 42;\n")
    return moduleDir
}

private fun run(projectDir: Path, vararg args: String): BuildResult =
    GradleRunner.create()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withArguments(*args, "--stacktrace")
        .build()

class FormatRequestSpec : ShouldSpec({

    should("keep the check compile quiet about autofixable findings when wrasseFormatRequest ran first") {
        val projectDir = Files.createTempDirectory("wrasse-format-request-")
        try {
            val moduleDir = writeFixtureProject(projectDir)
            val result = run(projectDir, ":module:wrasseFormatRequest", ":module:compileKotlin", "-PwrasseCheck")

            result.output shouldNotContain "no-semicolons"
            Files.exists(moduleDir.resolve("build/wrasse/main/format-request")) shouldBe false
            Files.readString(moduleDir.resolve("build/wrasse/main/patch/wrasse-fixes.txt")) shouldContain "edit:"

            run(projectDir, ":module:wrasseApply")
            Files.readString(moduleDir.resolve("src/main/kotlin/sample/Sample.kt")) shouldBe "package sample\n\nval answer = 42\n"
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    should("print autofixable findings on an ordinary check compile") {
        val projectDir = Files.createTempDirectory("wrasse-format-request-control-")
        try {
            writeFixtureProject(projectDir)
            val result = run(projectDir, ":module:compileKotlin", "-PwrasseCheck")

            result.output shouldContain "wrasse: no-semicolons: Unnecessary semicolon"
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    should("remove a leftover request through wrasseApply") {
        val projectDir = Files.createTempDirectory("wrasse-format-request-leftover-")
        try {
            val moduleDir = writeFixtureProject(projectDir)
            run(projectDir, ":module:wrasseFormatRequest")
            val request = moduleDir.resolve("build/wrasse/main/format-request")
            Files.exists(request) shouldBe true

            run(projectDir, ":module:wrasseApply")
            Files.exists(request) shouldBe false
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }
})
