package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.plugin.PLUGIN_ID
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services

private const val INTERNAL_ERROR_MESSAGE_PREFIX = "wrasse: wrasse internal error while checking this file "

/**
 * Proves the D24 amendment (design.md §12): any `Throwable` from wrasse's own code during
 * `WrassePlugin.checkFile` is caught at the file boundary, reported once naming the failure
 * through the compiler's `MessageCollector` at `INFO` severity (never as a `WrasseErrors`
 * diagnostic, so `-Werror`/`allWarningsAsErrors` can never turn it into a build failure), and
 * never costs the user their build — kotlinc keeps compiling, its own diagnostics and wrasse's
 * diagnostics for every other file survive untouched, and no partially-collected edit set ever
 * reaches the fix patch.
 *
 * Drives a real `K2JVMCompiler` invocation with [ThrowingTestRule] wired in through
 * [ThrowingRuleTestRegistrar] (a second `CompilerPluginRegistrar` found only via this module's
 * own test classpath) rather than any new production hook; the production registrar rides along
 * on the same compile but is neutralized with its own `enabled=false` plugin option.
 *
 * Configuration for [ThrowingRuleTestRegistrar] rides its own plugin options — parsed by
 * [ThrowingRuleTestCommandLineProcessor] into this exact compile's own `CompilerConfiguration`,
 * never `wrasse.json`/`wrasseMain` — so each compile is fully self-contained with no shared
 * mutable configuration state. Each `should` block still runs strictly sequentially (never
 * concurrently with another one from this spec): `K2JVMCompiler.exec` mutates process-global
 * compiler state internally, so two real compiles racing in the same JVM can misattribute or
 * drop each other's diagnostics — an embeddable-compiler limitation, not a wrasse one.
 *
 * The first `should` block runs two compiles to prove two properties independently: the crash's
 * own message (no accompanying error), and that a real compile error still surfaces normally
 * (`COMPILATION_ERROR`, never `INTERNAL_ERROR`) on a file that also has the crash. The second
 * compile does not also assert the crash's own message is present: it never is, the same
 * file-level suppression design.md §14 records for a genuine `WrasseErrors` `warn`
 * diagnostic on a file with a real compiler error applies here too, unaffected by this fix.
 * Reporting through the `MessageCollector` directly only stops `-Werror`/`allWarningsAsErrors`
 * from turning an *emitted* message into a build failure; it does not change whether one is
 * emitted at all for such a file.
 */
class InternalFailureIsolationSpec : BaseSpec({
    testExecutionMode = TestExecutionMode.Sequential

    should(
        "report once at info severity with the exception type and message, skip this file's own " +
            "wrasse diagnostics, and still let kotlinc report its own compile error for the same file",
    ) {
        val crashingSource = "sample/Sample.kt" to """
            package sample

            private val wrasseCrashTestMarker = 0

            fun sample(): Int = 1
            """
            .trimIndent()

        val crashResult = compileWithThrowingRule(listOf(crashingSource))

        crashResult.wrasseDiagnostics shouldHaveSize 1
        crashResult.wrasseDiagnostics[0].severity shouldBe CompilerMessageSeverity.INFO
        crashResult.wrasseDiagnostics[0].message shouldBe
            "$INTERNAL_ERROR_MESSAGE_PREFIX(IllegalStateException: $THROWING_TEST_RULE_CRASH_MESSAGE); " +
                "wrasse results for this file were skipped"

        val crashPlusTypeErrorSource = "sample/Sample.kt" to """
            package sample

            private val wrasseCrashTestMarker = 0

            fun sample(): Int {
                val bad: Int = "oops"
                return bad
            }
            """
            .trimIndent()

        val crashPlusTypeErrorResult = compileWithThrowingRule(listOf(crashPlusTypeErrorSource))

        crashPlusTypeErrorResult.exitCode shouldBe ExitCode.COMPILATION_ERROR
        crashPlusTypeErrorResult.diagnostics.any {
            it.severity == CompilerMessageSeverity.ERROR && !it.message.startsWith("wrasse:")
        } shouldBe true
    }

    should("never fail a compile that treats warnings as errors") {
        val crashingSource = "sample/Sample.kt" to """
            package sample

            private val wrasseCrashTestMarker = 0

            fun sample(): Int = 1
            """
            .trimIndent()

        val result = compileWithThrowingRule(listOf(crashingSource), allWarningsAsErrors = true)

        result.exitCode shouldBe ExitCode.OK
        result.wrasseDiagnostics shouldHaveSize 1
        result.wrasseDiagnostics[0].severity shouldBe CompilerMessageSeverity.INFO
    }

    should("not suppress a second file's normal wrasse diagnostics when the first file's rule throws") {
        val crashing = "sample/Crash.kt" to """
            package sample

            val wrasseCrashTestMarker = 0
            """
            .trimIndent()
        val clean = "sample/Clean.kt" to """
            package sample

            fun sampleB(): Int {
                val x = 1;
                return x
            }
            """
            .trimIndent()

        val result = compileWithThrowingRule(listOf(crashing, clean), withEditingRule = true)

        result.wrasseDiagnostics shouldHaveSize 2
        val crashDiagnostic = result.wrasseDiagnostics.single { it.location?.path?.endsWith("Crash.kt") == true }
        crashDiagnostic.severity shouldBe CompilerMessageSeverity.INFO
        crashDiagnostic.message shouldBe
            "$INTERNAL_ERROR_MESSAGE_PREFIX(IllegalStateException: $THROWING_TEST_RULE_CRASH_MESSAGE); " +
                "wrasse results for this file were skipped"
        val cleanDiagnostic = result.wrasseDiagnostics.single { it.location?.path?.endsWith("Clean.kt") == true }
        cleanDiagnostic.severity shouldBe CompilerMessageSeverity.WARNING
        cleanDiagnostic.message shouldBe "wrasse: no-semicolons: Unnecessary semicolon"
    }

    should("discard already-collected edits from another rule when a later rule throws mid-walk") {
        val fixOutputDir = Files.createTempDirectory("wrasse-throwing-rule-patch-")
        try {
            val source = "sample/Sample.kt" to """
                package sample

                fun sample(): Int {
                    val x = 1;
                    val wrasseCrashTestMarker = 0
                    return x
                }
                """
                .trimIndent()

            val result = compileWithThrowingRule(listOf(source), fixOutputDir = fixOutputDir, withEditingRule = true)

            result.wrasseDiagnostics shouldHaveSize 1
            result.wrasseDiagnostics[0].severity shouldBe CompilerMessageSeverity.INFO

            val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
            if (Files.exists(patchFile)) {
                WPatchReader.read(Files.readString(patchFile)).shouldBeEmpty()
            }
        } finally {
            fixOutputDir.toFile().deleteRecursively()
        }
    }
})

private class ThrowingRuleDiagnostic(
    val severity: CompilerMessageSeverity,
    val message: String,
    val location: CompilerMessageSourceLocation?,
)

private class ThrowingRuleCompileResult(val exitCode: ExitCode, val diagnostics: List<ThrowingRuleDiagnostic>) {
    val wrasseDiagnostics: List<ThrowingRuleDiagnostic>
        get() = diagnostics.filter { it.message.startsWith("wrasse:") }
}

private class ThrowingRuleDiagnosticCollector : MessageCollector {
    val diagnostics = mutableListOf<ThrowingRuleDiagnostic>()

    override fun clear() {
        diagnostics.clear()
    }

    override fun hasErrors(): Boolean = diagnostics.any { it.severity.isError }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        diagnostics.add(ThrowingRuleDiagnostic(severity, message, location))
    }
}

private fun compileWithThrowingRule(
    sources: List<Pair<String, String>>,
    fixOutputDir: Path? = null,
    withEditingRule: Boolean = false,
    allWarningsAsErrors: Boolean = false,
): ThrowingRuleCompileResult {
    val workDir = Files.createTempDirectory("wrasse-throwing-rule-")
    try {
        val srcDir = Files.createDirectories(workDir.resolve("src"))
        for ((path, content) in sources) {
            val file = srcDir.resolve(path)
            Files.createDirectories(file.parent)
            Files.write(file, content.toByteArray())
        }
        Files.write(workDir.resolve("wrasse.json"), "{\"rules\":{}}".toByteArray())

        val throwingRuleOptions = mutableListOf(
            "plugin:$THROWING_TEST_PLUGIN_ID:$WITH_EDITING_RULE_OPTION=$withEditingRule",
        )
        if (fixOutputDir != null) {
            throwingRuleOptions.add("plugin:$THROWING_TEST_PLUGIN_ID:$FIX_OUTPUT_DIR_OPTION=$fixOutputDir")
        }

        val registrarClassDir = File(ThrowingRuleTestRegistrar::class.java.protectionDomain.codeSource.location.toURI())
        val servicesFile = ThrowingRuleTestRegistrar::class.java.classLoader
            .getResources("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar")
            .toList()
            .map { url -> File(url.toURI()) }
            .single { file -> file.path.contains("${File.separator}test${File.separator}") }
        val registrarResourcesDir = servicesFile.parentFile.parentFile.parentFile

        val collector = ThrowingRuleDiagnosticCollector()
        val compiler = K2JVMCompiler()
        val stdlibPath = File(Unit::class.java.protectionDomain.codeSource.location.toURI()).absolutePath
        val args = K2JVMCompilerArguments().apply {
            freeArgs = sources.map { (path, _) -> srcDir.resolve(path).toString() }
            pluginClasspaths = arrayOf(registrarClassDir.absolutePath, registrarResourcesDir.absolutePath)
            pluginOptions = (throwingRuleOptions + "plugin:$PLUGIN_ID:enabled=false").toTypedArray()
            noReflect = true
            noJdk = true
            jvmTarget = "1.8"
            destination = workDir.resolve("out").toString()
            classpath = stdlibPath
            noStdlib = true
            this.allWarningsAsErrors = allWarningsAsErrors
        }

        val exitCode = compiler.exec(collector, Services.EMPTY, args)
        return ThrowingRuleCompileResult(exitCode, collector.diagnostics)
    } finally {
        workDir.toFile().deleteRecursively()
    }
}
