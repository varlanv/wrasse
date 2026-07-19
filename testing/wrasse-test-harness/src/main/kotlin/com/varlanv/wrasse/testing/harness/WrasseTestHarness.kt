package com.varlanv.wrasse.testing.harness

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class WrasseTestHarness(
    private val wrasseConfig: String,
    private val warnOnly: Boolean = false,
    private val extraConfigFiles: Map<String, String> = emptyMap(),
    private val fixOutputDir: Path? = null,
    private val multiPlatformCommonSources: Set<String> = emptySet(),
    private val dumpResolvedUsage: Boolean = false,
) {

    companion object {
        private val pluginClasspath: String by lazy {
            val marker = com.varlanv.wrasse.plugin.internal.WrasseCompilerPluginRegistrar::class.java
            val location = marker.protectionDomain.codeSource.location
            File(location.toURI()).absolutePath
        }

        private val kotlinStdlibPath: String? by lazy {
            System.getProperty("wrasse.harness.stdlibPath")?.let { return@lazy it }
            val marker = kotlin.Unit::class.java
            val location = marker.protectionDomain?.codeSource?.location ?: return@lazy null
            File(location.toURI()).absolutePath
        }

        private const val PLUGIN_ID = "com.varlanv.wrasse"
    }

    fun compile(sources: List<TestSource>): CompilationResult {
        val workDir = Files.createTempDirectory("wrasse-harness-")
        try {
            return doCompile(workDir, sources)
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    fun compile(sources: List<TestSource>, workDir: Path): CompilationResult =
        doCompile(workDir, sources)

    fun sourcePath(workDir: Path, source: TestSource): Path =
        workDir.resolve("src").resolve(source.path)

    private fun doCompile(workDir: Path, sources: List<TestSource>): CompilationResult {
        val srcDir = Files.createDirectories(workDir.resolve("src"))
        for (source in sources) {
            val file = srcDir.resolve(source.path)
            Files.createDirectories(file.parent)
            Files.write(file, source.content.toByteArray())
        }
        Files.write(workDir.resolve("wrasse.json"), wrasseConfig.toByteArray())
        for ((name, content) in extraConfigFiles) {
            val file = workDir.resolve(name)
            Files.createDirectories(file.parent)
            Files.write(file, content.toByteArray())
        }

        val wrassePluginOptions = mutableListOf<String>()
        if (warnOnly) {
            wrassePluginOptions.add("plugin:$PLUGIN_ID:warnOnly=true")
        }
        if (fixOutputDir != null) {
            wrassePluginOptions.add("plugin:$PLUGIN_ID:fix=true")
            wrassePluginOptions.add("plugin:$PLUGIN_ID:fixOutputDir=$fixOutputDir")
        }
        if (dumpResolvedUsage) {
            wrassePluginOptions.add("plugin:$PLUGIN_ID:dumpResolvedUsage=true")
        }

        val collector = DiagnosticCollector()
        val compiler = K2JVMCompiler()
        val args = K2JVMCompilerArguments().apply {
            freeArgs = sources.map { srcDir.resolve(it.path).toString() }
            pluginClasspaths = arrayOf(pluginClasspath)
            if (wrassePluginOptions.isNotEmpty()) {
                pluginOptions = wrassePluginOptions.toTypedArray()
            }
            noReflect = true
            noJdk = true
            jvmTarget = "1.8"
            destination = workDir.resolve("out").toString()
            if (multiPlatformCommonSources.isNotEmpty()) {
                multiPlatform = true
                commonSources = multiPlatformCommonSources.map { srcDir.resolve(it).toString() }.toTypedArray()
            }
            val cp = kotlinStdlibPath
            if (cp != null) {
                classpath = cp
                noStdlib = true
            }
        }

        val exitCode = compiler.exec(collector, Services.EMPTY, args)

        return CompilationResult(
            exitCode = exitCode,
            diagnostics = collector.diagnostics,
        )
    }
}

class TestSource(val path: String, val content: String)

class CompilationResult(
    val exitCode: org.jetbrains.kotlin.cli.common.ExitCode,
    val diagnostics: List<TestDiagnostic>,
) {
    val wrasseDiagnostics: List<TestDiagnostic>
        get() = diagnostics.filter { it.message.startsWith("wrasse:") }
}

class TestDiagnostic(
    val severity: CompilerMessageSeverity,
    val message: String,
    val location: CompilerMessageSourceLocation?,
)

private class DiagnosticCollector : MessageCollector {
    val diagnostics = mutableListOf<TestDiagnostic>()

    override fun clear() {
        diagnostics.clear()
    }

    override fun hasErrors(): Boolean =
        diagnostics.any { it.severity.isError }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        diagnostics.add(TestDiagnostic(severity, message, location))
    }
}
