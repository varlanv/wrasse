package com.varlanv.gradle.plugin

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * A minimal integration probe for the exact `forkGradle` pattern used by the real root
 * `build.gradle.kts` (`wrasseFix`/`wrasseLint`): a task that shells out to a nested `./gradlew`
 * invocation and either propagates or suppresses that invocation's failure depending on
 * `failOnError`. Root `build.gradle.kts` has no test source set of its own to host a unit test
 * against, and a full TestKit run of the real, multi-module `wrasseFix` task would mean re-running
 * this whole repository's build inside a test — disproportionate for what is a generic
 * "does a forked process's exit code propagate" question. Instead, this reproduces the pattern
 * verbatim in a throwaway fixture project (its own copy of the real repo's Gradle wrapper, so the
 * nested `./gradlew` reuses the already-downloaded local distribution) and drives it against a
 * task that deliberately fails, independent of any wrasse-specific logic.
 */
class ForkGradleFailurePropagationSpec : ShouldSpec({

    should("propagate the nested build's failure when failOnError is true") {
        val projectDir = Files.createTempDirectory("fork-gradle-propagate-")
        try {
            writeFixture(projectDir)

            val result = runCatching {
                GradleRunner.create()
                    .withProjectDir(projectDir.toFile())
                    .withArguments("outerPropagate", "--stacktrace")
                    .build()
            }

            val failure = result.exceptionOrNull()
            (failure != null) shouldBe true
            (failure!!.message ?: "").contains("Forked gradle task failed") shouldBe true
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    should("suppress the nested build's failure when failOnError is false") {
        val projectDir = Files.createTempDirectory("fork-gradle-suppress-")
        try {
            writeFixture(projectDir)

            val result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("outerSuppress", "--stacktrace")
                .build()

            result.output.contains("BUILD SUCCESSFUL") shouldBe true
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    should("not fail when the nested build itself succeeds") {
        val projectDir = Files.createTempDirectory("fork-gradle-ok-")
        try {
            writeFixture(projectDir)

            val result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("outerOk", "--stacktrace")
                .build()

            result.output.contains("BUILD SUCCESSFUL") shouldBe true
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }
})

private val realRepoRoot: Path = Path.of(System.getProperty("wrasse.realRepoRoot") ?: error("system property 'wrasse.realRepoRoot' not set"))

private fun writeFixture(projectDir: Path) {
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"fork-gradle-fixture\"\n")
    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        """
        fun Project.forkGradle(
            vararg args: String,
            failOnError: Boolean = true,
        ) {
            val osName = providers.systemProperty("os.name").get()
            val gradlew = file(
                if (osName.lowercase().contains("windows")) "gradlew.bat" else "gradlew"
            ).absolutePath
            val result = providers.exec {
                commandLine(gradlew, "--no-daemon", *args)
                isIgnoreExitValue = true
            }
            val stdout = result.standardOutput.asText.get()
            val stderr = result.standardError.asText.get()
            if (stdout.isNotBlank()) println(stdout)
            if (stderr.isNotBlank()) System.err.println(stderr)
            if (failOnError && result.result.get().exitValue != 0) {
                throw GradleException("Forked gradle task failed")
            }
        }

        tasks.register("innerFail") {
            doLast { throw GradleException("simulated inner failure") }
        }

        tasks.register("innerOk") {
            doLast { }
        }

        tasks.register("outerPropagate") {
            doLast { forkGradle("innerFail", failOnError = true) }
        }

        tasks.register("outerSuppress") {
            doLast { forkGradle("innerFail", failOnError = false) }
        }

        tasks.register("outerOk") {
            doLast { forkGradle("innerOk", failOnError = true) }
        }
        """.trimIndent(),
    )
    copyWrapper(projectDir)
}

private fun copyWrapper(projectDir: Path) {
    val gradlewSource = realRepoRoot.resolve("gradlew")
    Files.copy(gradlewSource, projectDir.resolve("gradlew"), StandardCopyOption.REPLACE_EXISTING)
    projectDir.resolve("gradlew").toFile().setExecutable(true)

    val gradlewBatSource = realRepoRoot.resolve("gradlew.bat")
    Files.copy(gradlewBatSource, projectDir.resolve("gradlew.bat"), StandardCopyOption.REPLACE_EXISTING)

    val wrapperDir = Files.createDirectories(projectDir.resolve("gradle/wrapper"))
    Files.copy(
        realRepoRoot.resolve("gradle/wrapper/gradle-wrapper.jar"),
        wrapperDir.resolve("gradle-wrapper.jar"),
        StandardCopyOption.REPLACE_EXISTING,
    )
    Files.copy(
        realRepoRoot.resolve("gradle/wrapper/gradle-wrapper.properties"),
        wrapperDir.resolve("gradle-wrapper.properties"),
        StandardCopyOption.REPLACE_EXISTING,
    )
}
