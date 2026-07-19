package com.varlanv.gradle.plugin

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

private fun sha256Hex(content: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    val chars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        chars[i * 2] = HEX_DIGITS[v ushr 4]
        chars[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
    }
    return String(chars)
}

private val catalogPath: String =
    (System.getProperty("wrasse.realRepoCatalogPath") ?: error("system property 'wrasse.realRepoCatalogPath' not set"))
        .replace("\\", "\\\\")

private fun writeFixtureProject(projectDir: Path, moduleDependenciesBlock: String) {
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
    Files.writeString(projectDir.resolve("testing/common-test/build.gradle.kts"), "")
    val moduleDir = Files.createDirectories(projectDir.resolve("module"))
    Files.writeString(
        moduleDir.resolve("build.gradle.kts"),
        """
        plugins {
            id("com.varlanv.wrasse.gradle.internal-gradle-convention-plugin")
        }
        $moduleDependenciesBlock
        """.trimIndent(),
    )
}

private fun writeSourceAndPatch(projectDir: Path, sourceContent: String, findText: String, replaceText: String): Path {
    val srcDir = Files.createDirectories(projectDir.resolve("module/src/main/kotlin"))
    val sourceFile = srcDir.resolve("Sample.kt")
    Files.writeString(sourceFile, sourceContent)

    val start = sourceContent.indexOf(findText)
    check(start >= 0) { "'$findText' not found in fixture source" }
    val end = start + findText.length

    val buildWrasseDir = Files.createDirectories(projectDir.resolve("module/build/wrasse"))
    Files.writeString(
        buildWrasseDir.resolve("wrasse-fixes.txt"),
        "# wrasse-fixes v1\n" +
            "file:${sourceFile.toAbsolutePath()}\n" +
            "hash:${sha256Hex(sourceContent)}\n" +
            "edit:$start:$end:$replaceText\n",
    )
    return sourceFile
}

class WrasseApplyClasspathSpec : ShouldSpec({

    should("apply a pending patch even when the module's own wrasse-related deps are all compileOnly") {
        val projectDir = Files.createTempDirectory("wrasse-apply-classpath-")
        try {
            writeFixtureProject(
                projectDir,
                """
                dependencies {
                    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
                }
                """.trimIndent(),
            )
            val sourceContent = "package sample\n\nclass Foo\n"
            val sourceFile = writeSourceAndPatch(projectDir, sourceContent, "Foo", "Bar")

            val result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments(":module:wrasseApply", "--stacktrace")
                .build()

            result.output.contains("BUILD SUCCESSFUL") shouldBe true
            Files.readString(sourceFile) shouldBe "package sample\n\nclass Bar\n"
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    should("apply a pending patch when the module declares no wrasse-related dependencies at all") {
        val projectDir = Files.createTempDirectory("wrasse-apply-classpath-nodeps-")
        try {
            writeFixtureProject(projectDir, "")
            val sourceContent = "package sample\n\nclass Alpha\n"
            val sourceFile = writeSourceAndPatch(projectDir, sourceContent, "Alpha", "Beta")

            val result = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments(":module:wrasseApply", "--stacktrace")
                .build()

            result.output.contains("BUILD SUCCESSFUL") shouldBe true
            Files.readString(sourceFile) shouldBe "package sample\n\nclass Beta\n"
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }
})
