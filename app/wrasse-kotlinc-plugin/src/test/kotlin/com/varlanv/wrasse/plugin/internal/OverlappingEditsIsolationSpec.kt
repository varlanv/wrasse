package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.lang.WReportReader
import com.varlanv.wrasse.plugin.PLUGIN_ID
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services

/**
 * Proves that two rules attaching overlapping edits to the same file no longer costs the file its
 * diagnostics (see the D24 amendment covered by [InternalFailureIsolationSpec]): both
 * `range-conventional` and `long-numerical-values` still report on `0.rangeTo(1000000)`, and the
 * emitted patch carries only the one edit [com.varlanv.wrasse.model.EditPlan.finalEdits] kept —
 * `range-conventional`'s whole-call rewrite, which starts earliest.
 */
class OverlappingEditsIsolationSpec : BaseSpec({
    testExecutionMode = TestExecutionMode.Sequential

    should("report both overlapping findings and patch only the surviving, earlier-starting edit") {
        val fixOutputDir = Files.createTempDirectory("wrasse-overlapping-edits-patch-")
        try {
            val source = "sample/Sample.kt" to """
                package sample

                val r = 0.rangeTo(1000000)
                """
                .trimIndent()

            val result = compileWithOverlappingEditsRules(source, fixOutputDir)

            result.diagnostics shouldHaveSize 2
            val sorted = result.diagnostics.sortedBy { it.message }
            sorted[0].severity shouldBe CompilerMessageSeverity.ERROR
            sorted[0].message shouldBe
                "wrasse: long-numerical-values: Long numerical literal without underscore separators"
            sorted[1].severity shouldBe CompilerMessageSeverity.ERROR
            sorted[1].message shouldBe "wrasse: range-conventional: Replace rangeTo call with the .. operator"

            val patchFile = fixOutputDir.resolve("patch").resolve("wrasse-fixes.txt")
            val fileEdits = WPatchReader.read(Files.readString(patchFile))
            fileEdits shouldHaveSize 1
            fileEdits[0].edits shouldHaveSize 1
            fileEdits[0].edits[0].replacement shouldBe "0..1000000"
        } finally {
            fixOutputDir.toFile().deleteRecursively()
        }
    }

    should("record the dropped overlapping edit's diagnostic as not fixable") {
        val fixOutputDir = Files.createTempDirectory("wrasse-overlapping-edits-report-")
        try {
            val source = "sample/Sample.kt" to """
                package sample

                val r = 0.rangeTo(1000000)
                """
                .trimIndent()

            compileWithOverlappingEditsRules(source, fixOutputDir)

            val reportFile = fixOutputDir.resolve("patch").resolve("wrasse-report.txt")
            val entries = WReportReader.read(Files.readString(reportFile))
            entries shouldHaveSize 1
            entries[0].diagnostics
                .sortedBy { it.message }
                .map { "${it.message}:${if (it.fixable) 1 else 0}" } shouldBe
                listOf(
                    "long-numerical-values: Long numerical literal without underscore separators:0",
                    "range-conventional: Replace rangeTo call with the .. operator:1",
                )
        } finally {
            fixOutputDir.toFile().deleteRecursively()
        }
    }
})

private class OverlappingEditsDiagnostic(
    val severity: CompilerMessageSeverity,
    val message: String,
    val location: CompilerMessageSourceLocation?,
)

private class OverlappingEditsDiagnosticCollector : MessageCollector {
    val all = mutableListOf<OverlappingEditsDiagnostic>()

    override fun clear() {
        all.clear()
    }

    override fun hasErrors(): Boolean = all.any { it.severity.isError }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        all.add(OverlappingEditsDiagnostic(severity, message, location))
    }

    val diagnostics: List<OverlappingEditsDiagnostic>
        get() = all.filter { it.message.startsWith("wrasse:") }
}

private class OverlappingEditsCompileResult(val diagnostics: List<OverlappingEditsDiagnostic>)

private fun compileWithOverlappingEditsRules(
    source: Pair<String, String>,
    fixOutputDir: Path,
): OverlappingEditsCompileResult {
    val workDir = Files.createTempDirectory("wrasse-overlapping-edits-")
    try {
        val (path, content) = source
        val srcDir = Files.createDirectories(workDir.resolve("src"))
        val file = srcDir.resolve(path)
        Files.createDirectories(file.parent)
        Files.write(file, content.toByteArray())

        val registrarClassDir = File(
            OverlappingEditsTestRegistrar::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val servicesFile = OverlappingEditsTestRegistrar::class.java.classLoader
            .getResources("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar")
            .toList()
            .map { url -> File(url.toURI()) }
            .single { file -> file.path.contains("${File.separator}test${File.separator}") }
        val registrarResourcesDir = servicesFile.parentFile.parentFile.parentFile

        val collector = OverlappingEditsDiagnosticCollector()
        val compiler = K2JVMCompiler()
        val stdlibPath = File(Unit::class.java.protectionDomain.codeSource.location.toURI()).absolutePath
        val args = K2JVMCompilerArguments().apply {
            freeArgs = listOf(file.toString())
            pluginClasspaths = arrayOf(registrarClassDir.absolutePath, registrarResourcesDir.absolutePath)
            pluginOptions =
                arrayOf(
                    "plugin:$OVERLAPPING_EDITS_TEST_PLUGIN_ID:$OVERLAPPING_EDITS_FIX_OUTPUT_DIR_OPTION=$fixOutputDir",
                    "plugin:$PLUGIN_ID:enabled=false",
                )
            noReflect = true
            noJdk = true
            jvmTarget = "1.8"
            destination = workDir.resolve("out").toString()
            classpath = stdlibPath
            noStdlib = true
        }

        compiler.exec(collector, Services.EMPTY, args)
        return OverlappingEditsCompileResult(collector.diagnostics)
    } finally {
        workDir.toFile().deleteRecursively()
    }
}
