package com.varlanv.wrasse.gradle

import java.io.File
import java.net.URLClassLoader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.UntrackedTask

private const val PLUGIN_ID = "com.varlanv.wrasse"
private const val GROUP = "wrasse"
private const val PATCH_DIR = "patch"
private const val PATCH_FILE = "wrasse-fixes.txt"
private const val REPORT_FILE = "wrasse-report.txt"
private const val REQUEST_FILE = "format-request"
private val COMPILE_TASK_NAME = Regex("^compile(.*)Kotlin$")
private val KOTLIN_PLUGIN_IDS =
    listOf("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.android")

/**
 * `wrasse { }`: [enabled] runs the compiler plugin in every Kotlin compile (default true);
 * [warnOnly] keeps error-level findings from failing the compile itself, leaving `wrasseLint` as
 * the gate (default true); [configFile] is the `wrasse.json` the compiles depend on, found by
 * walking up from the project directory; [version] is the compiler plugin artifact version.
 */
abstract class WrasseExtension {
    abstract val enabled: Property<Boolean>
    abstract val warnOnly: Property<Boolean>
    abstract val configFile: RegularFileProperty
    abstract val version: Property<String>
}

/**
 * Applies wrasse to one project and touches nothing outside it, so it holds under parallel
 * execution, the configuration cache and isolated projects. Each Kotlin compile gets its own
 * `build/wrasse/<compilation>` directory: `patch/` inside it is a declared output (a build-cache
 * hit restores the journal and the report) and the discovered `wrasse.json` is a declared input.
 * `wrasseLint` and `wrasseFormat` mark their compiles quiet through a request file and print
 * findings from the report afterwards, so the output is the same whether the compile ran or not.
 */
class WrasseGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("wrasse", WrasseExtension::class.java)
        extension.enabled.convention(true)
        extension.warnOnly.convention(true)
        extension.version.convention(WrasseVersion.VALUE)
        extension.configFile.convention(project.layout.file(project.provider { discoverConfig(project.projectDir) }))

        val tool = project.configurations.register("wrasseTool") { config ->
            config.isCanBeConsumed = false
            config.isCanBeResolved = true
            config.defaultDependencies {
                it.add(project.dependencies.create("com.varlanv.wrasse:compiler-plugin:${extension.version.get()}"))
            }
        }
        val toolClasspath = project.files(tool)
        val wrasseDir = project.layout.buildDirectory.dir("wrasse")
        val compilationDirs = project.provider {
            project.tasks.names.mapNotNull { compilationName(it) }.map { File(wrasseDir.get().asFile, it) }
        }
        val debugPerformance = project.providers.gradleProperty("wrasseDebugPerformance").map { true }.orElse(false)

        fun request(name: String, formatting: Boolean, description: String) =
            project.tasks.register(name, WrasseRequestTask::class.java) { task ->
                task.group = GROUP
                task.description = description
                task.compilationDirs.set(compilationDirs)
                task.formatting.set(formatting)
                task.debugPerformance.set(debugPerformance)
            }
        val formatRequest = request("wrasseFormatRequest", true, "Marks the next wrasse compiles of this project as a format run")
        val lintRequest = request("wrasseLintRequest", false, "Marks the next wrasse compiles of this project as a lint run")
        val apply = project.tasks.register("wrasseApply", WrasseApplyTask::class.java) { task ->
            task.group = GROUP
            task.description = "Applies the wrasse patches recorded by this project's compiles"
            task.toolClasspath.from(toolClasspath)
            task.wrasseDir.set(wrasseDir)
        }
        val lint = project.tasks.register("wrasseLint", WrasseLintTask::class.java) { task ->
            task.group = GROUP
            task.description = "Reports every wrasse finding of this project and fails on error-level ones"
            task.toolClasspath.from(toolClasspath)
            task.wrasseDir.set(wrasseDir)
            task.dependsOn(lintRequest)
        }
        val format = project.tasks.register("wrasseFormat") { task ->
            task.group = GROUP
            task.description = "Formats and autofixes this project's sources with wrasse"
            task.dependsOn(formatRequest)
            task.finalizedBy(apply)
        }

        var wired = false
        for (pluginId in KOTLIN_PLUGIN_IDS) {
            project.pluginManager.withPlugin(pluginId) {
                if (wired) return@withPlugin
                val compileClass = kotlinCompileClass(project, pluginId) ?: return@withPlugin
                wired = true
                val compiles = project.tasks.withType(compileClass)
                wireCompiles(project, extension, wrasseDir.get().asFile, compiles)
                compiles.configureEach { it.mustRunAfter(formatRequest, lintRequest) }
                lint.configure { it.dependsOn(compiles) }
                format.configure { it.dependsOn(compiles) }
                apply.configure { it.mustRunAfter(compiles) }
            }
        }
    }

    /**
     * Adds the compiler-plugin args from [Project.afterEvaluate] so they land after a consumer's
     * own `compilerOptions.freeCompilerArgs.set(...)`; the property is final by the time a task
     * action runs.
     */
    private fun wireCompiles(
        project: Project,
        extension: WrasseExtension,
        wrasseDir: File,
        compiles: TaskCollection<out Task>,
    ) {
        project.dependencies.addProvider(
            "kotlinCompilerPluginClasspath",
            extension.version.map { "com.varlanv.wrasse:compiler-plugin:$it" },
        )
        compiles.configureEach { compile ->
            val fixOutputDir = File(wrasseDir, compilationName(compile.name) ?: return@configureEach)
            compile.outputs.dir(File(fixOutputDir, PATCH_DIR)).withPropertyName("wrassePatch")
            compile.inputs.file(extension.configFile).optional().withPathSensitivity(PathSensitivity.NONE).withPropertyName("wrasseConfig")
        }
        project.afterEvaluate {
            val buildDirPath = project.layout.buildDirectory.get().asFile.absolutePath
            compiles.configureEach { compile ->
                val fixOutputDir = File(wrasseDir, compilationName(compile.name) ?: return@configureEach)
                freeCompilerArgs(compile).addAll(
                    extension.enabled.zip(extension.warnOnly) { enabled, warnOnly ->
                        listOf(
                            "-P", "plugin:$PLUGIN_ID:enabled=$enabled",
                            "-P", "plugin:$PLUGIN_ID:warnOnly=$warnOnly",
                            "-P", "plugin:$PLUGIN_ID:fixOutputDir=${fixOutputDir.absolutePath}",
                            "-P", "plugin:$PLUGIN_ID:excludedRoot=$buildDirPath",
                        )
                    },
                )
            }
        }
    }
}

@UntrackedTask(because = "the request file is consumed by the compile that follows")
abstract class WrasseRequestTask : DefaultTask() {
    @get:Internal
    abstract val compilationDirs: ListProperty<File>

    @get:Input
    abstract val formatting: Property<Boolean>

    @get:Input
    abstract val debugPerformance: Property<Boolean>

    @TaskAction
    fun write() {
        val content = StringBuilder("timestamp=${System.currentTimeMillis()}\nquiet=true\n")
        if (formatting.get()) content.append("formatting=true\n")
        if (debugPerformance.get()) content.append("debugPerformance=true\n")
        for (dir in compilationDirs.get()) {
            dir.mkdirs()
            File(dir, REQUEST_FILE).writeText(content.toString())
        }
    }
}

@UntrackedTask(because = "it rewrites the project sources from state left by compiles")
abstract class WrasseApplyTask : DefaultTask() {
    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val wrasseDir: DirectoryProperty

    @TaskAction
    fun apply() {
        val dir = wrasseDir.get().asFile
        if (!dir.isDirectory) return
        if (hasEntries(dir, PATCH_FILE, "file:")) {
            val exitCode = withTool(toolClasspath.files, "WPatchApplierKt", "runApplier", dir) as Int
            if (exitCode != 0) throw GradleException("wrasse could not apply every patch under ${dir.absolutePath}")
        }
        deleteRequests(dir)
        if (hasEntries(dir, REPORT_FILE, "diag:")) for (line in replay(dir, toolClasspath.files)) logger.lifecycle(line)
    }
}

@UntrackedTask(because = "it prints findings recorded by compiles and must run every time")
abstract class WrasseLintTask : DefaultTask() {
    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val wrasseDir: DirectoryProperty

    @TaskAction
    fun lint() {
        val dir = wrasseDir.get().asFile
        if (!dir.isDirectory) return
        deleteRequests(dir)
        if (!hasEntries(dir, REPORT_FILE, "diag:")) return
        val lines = replay(dir, toolClasspath.files)
        for (line in lines) logger.lifecycle(line)
        val errors = lines.count { it.startsWith("e: ") }
        if (errors > 0) throw GradleException("wrasse found $errors error-level violation(s) in ${path.substringBeforeLast(':')}")
    }
}

private fun compilationName(taskName: String): String? =
    COMPILE_TASK_NAME.matchEntire(taskName)?.groupValues?.get(1)?.replaceFirstChar { it.lowercaseChar() }?.ifEmpty { "main" }

private fun discoverConfig(start: File): File? =
    generateSequence(start) { it.parentFile }
        .flatMap { dir -> sequenceOf(File(dir, "wrasse.json"), File(dir, "wrasse.jsonc")) }
        .firstOrNull { it.isFile }

private fun kotlinCompileClass(project: Project, pluginId: String): Class<out Task>? {
    val plugin = project.plugins.findPlugin(pluginId) ?: return null
    return runCatching { plugin.javaClass.classLoader.loadClass("org.jetbrains.kotlin.gradle.tasks.KotlinCompile") }
        .getOrNull()
        ?.asSubclass(Task::class.java)
}

@Suppress("UNCHECKED_CAST")
private fun freeCompilerArgs(task: Task): ListProperty<String> {
    val options = task.javaClass.getMethod("getCompilerOptions").invoke(task)
    return options.javaClass.getMethod("getFreeCompilerArgs").invoke(options) as ListProperty<String>
}

private fun hasEntries(dir: File, fileName: String, linePrefix: String): Boolean =
    dir.walkTopDown().any { f -> f.isFile && f.name == fileName && f.useLines { lines -> lines.any { it.startsWith(linePrefix) } } }

private fun deleteRequests(dir: File) {
    dir.walkTopDown().filter { it.isFile && it.name == REQUEST_FILE }.forEach { it.delete() }
}

@Suppress("UNCHECKED_CAST")
private fun replay(dir: File, classpath: Set<File>): List<String> =
    withTool(classpath, "WReportReplayKt", "replayReports", dir) as List<String>

private fun withTool(classpath: Set<File>, className: String, method: String, dir: File): Any {
    val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
    return URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
        Class.forName("com.varlanv.wrasse.lang.$className", true, loader)
            .getMethod(method, List::class.java)
            .invoke(null, listOf(dir.absolutePath))
    }
}
