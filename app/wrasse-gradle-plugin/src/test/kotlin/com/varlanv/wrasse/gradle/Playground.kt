package com.varlanv.wrasse.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

internal val KOTLIN_VERSION: String = System.getProperty("wrasse.kotlinVersion") ?: error("wrasse.kotlinVersion not set")

internal const val SEMICOLON_SOURCE = "package sample\n\nval answer = 42;\n"
internal const val CLEAN_SOURCE = "package sample\n\nval answer = 42\n"
internal const val ERROR_CONFIG = """{"rules":{"no-semicolons":{"level":"error"}}}"""
internal const val WARN_CONFIG = """{"rules":{"no-semicolons":{"level":"warn"}}}"""
internal const val MAIN_SOURCE_ROOT = "src/main/kotlin"
internal const val TEST_SOURCE_ROOT = "src/test/kotlin"

/** A throwaway multi-module consumer build that applies the plugin under test to each module. */
internal class Playground(val dir: Path) {
    private val modules = LinkedHashSet<String>()

    fun settings(extraProperties: String = "") {
        Files.writeString(
            dir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
            }
            rootProject.name = "playground"
            include(${modules.joinToString { "\"$it\"" }})
            """.trimIndent(),
        )
        Files.writeString(dir.resolve("build.gradle.kts"), "")
        Files.writeString(
            dir.resolve("gradle.properties"),
            "org.gradle.jvmargs=-Xmx1g\n$extraProperties",
        )
    }

    fun config(json: String) {
        Files.writeString(dir.resolve("wrasse.json"), json)
    }

    fun module(
        name: String,
        sources: Map<String, String>,
        extension: String = "",
        afterExtension: String = "",
    ): Path {
        val moduleDir = rawModule(
            name,
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("com.varlanv.wrasse")
            }

            wrasse {
                $extension
            }

            $afterExtension
            """.trimIndent(),
        )
        for ((path, content) in sources) source(name, path, content)
        return moduleDir
    }

    fun rawModule(name: String, script: String): Path {
        modules.add(name)
        val moduleDir = Files.createDirectories(dir.resolve(name))
        Files.writeString(moduleDir.resolve("build.gradle.kts"), script)
        return moduleDir
    }

    fun rootFile(relativePath: String, content: String): Path {
        val file = dir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }

    fun source(
        module: String,
        path: String,
        content: String,
        sourceRoot: String = MAIN_SOURCE_ROOT,
    ): Path {
        val file = dir.resolve(module).resolve(sourceRoot).resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }

    fun sourceUri(
        module: String,
        path: String,
        sourceRoot: String = MAIN_SOURCE_ROOT,
    ): String = dir.resolve(module).resolve(sourceRoot).resolve(path).toUri().toString()

    fun rawFile(
        module: String,
        relativePath: String,
        content: String,
    ): Path {
        val file = dir.resolve(module).resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }

    fun runner(vararg args: String): GradleRunner = GradleRunner.create()
        .withPluginClasspath()
        .withProjectDir(dir.toFile())
        .withArguments(*args, "--stacktrace")

    fun run(vararg args: String): BuildResult = runner(*args).build()

    fun runAndFail(vararg args: String): BuildResult = runner(*args).buildAndFail()

    fun delete() {
        dir.toFile().deleteRecursively()
    }

    companion object {
        fun create(prefix: String): Playground = Playground(Files.createTempDirectory("wrasse-$prefix-"))
    }
}

internal fun String.countOf(needle: String): Int {
    var count = 0
    var index = indexOf(needle)
    while (index >= 0) {
        count++
        index = indexOf(needle, index + needle.length)
    }
    return count
}
