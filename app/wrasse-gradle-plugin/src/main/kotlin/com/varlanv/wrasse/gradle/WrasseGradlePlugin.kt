package com.varlanv.wrasse.gradle

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.Callable
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.UntrackedTask
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener

private const val PLUGIN_ID = "com.varlanv.wrasse"
private const val GROUP = "wrasse"
private const val WRASSE_DIR = "wrasse"
private const val PATCH_DIR = "patch"
private const val PATCH_FILE = "wrasse-fixes.txt"
private const val REPORT_FILE = "wrasse-report.txt"
private const val REQUEST_FILE = "format-request"
private const val KAPT_STUB_TASK_CLASS = "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask"
private val EXTENDS_ENTRY = Regex("\"extends\"\\s*:\\s*\"([^\"]+)\"")
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
 * hit restores the journal and the report) and the discovered `wrasse.json`, with every config it
 * extends, is a declared input. `wrasseLint` and `wrasseFormat` mark their compiles quiet through
 * a request file and print findings from the report afterwards, so the output is the same whether
 * the compile ran or not.
 */
class WrasseGradlePlugin @Inject constructor(
    private val listeners: BuildEventsListenerRegistry,
) : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("wrasse", WrasseExtension::class.java)
        extension.enabled.convention(true)
        extension.warnOnly.convention(true)
        extension.version.convention(WrasseVersion.VALUE)
        extension.configFile.convention(project.layout.file(project.provider { discoverConfig(project.projectDir) }))

        val tool = project.configurations.register("wrasseTool") { config ->
            config.isCanBeConsumed = false
            config.isCanBeResolved = true
            config.attributes { attributes ->
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.LIBRARY),
                )
            }
            config.defaultDependencies {
                it.add(project.dependencies.create("com.varlanv.wrasse:compiler-plugin:${extension.version.get()}"))
            }
        }
        val toolClasspath = project.files(tool)
        val wrasseDir = project.layout.buildDirectory.dir(WRASSE_DIR)
        val compilationDirs = wrasseDir.map { dir ->
            project.tasks.names.filter { isCompileTaskName(it) }.map { File(dir.asFile, compilationName(it)) }
        }
        val debugPerformance = project.providers.gradleProperty("wrasseDebugPerformance").map { true }.orElse(false)

        val cleanup = project.gradle.sharedServices.registerIfAbsent(
            "wrasse-request-cleanup${project.path}",
            WrasseRequestCleanupService::class.java,
        ) { spec -> spec.parameters.compilationDirs.set(compilationDirs) }
        listeners.onTaskCompletion(cleanup)

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
        formatRequest.configure { it.mustRunAfter(lintRequest) }
        val apply = project.tasks.register("wrasseApply", WrasseApplyTask::class.java) { task ->
            task.group = GROUP
            task.description = "Applies the wrasse patches recorded by this project's compiles"
            task.toolClasspath.from(toolClasspath)
            task.wrasseDir.set(wrasseDir)
            task.projectDir.set(project.layout.projectDirectory)
        }
        val lint = project.tasks.register("wrasseLint", WrasseLintTask::class.java) { task ->
            task.group = GROUP
            task.description = "Reports every wrasse finding of this project and fails on error-level ones"
            task.toolClasspath.from(toolClasspath)
            task.wrasseDir.set(wrasseDir)
            task.projectDir.set(project.layout.projectDirectory)
            task.projectPath.set(project.path)
            task.dependsOn(lintRequest)
        }
        project.tasks.register("wrasseFormat") { task ->
            task.group = GROUP
            task.description = "Formats and autofixes this project's sources with wrasse"
            task.dependsOn(formatRequest, apply)
        }

        var wired = false
        for (pluginId in KOTLIN_PLUGIN_IDS) {
            project.pluginManager.withPlugin(pluginId) {
                if (wired) return@withPlugin
                val compileClass = kotlinCompileClass(project, pluginId) ?: return@withPlugin
                wired = true
                val compiles = project.tasks.withType(compileClass)
                wireCompiles(project, extension, wrasseDir, compiles)
                compiles.configureEach { it.mustRunAfter(formatRequest, lintRequest) }
                lint.configure { task ->
                    task.dependsOn(compiles)
                    task.compileSources.from(Callable { compiles.map { compile -> sourcesOf(compile) } })
                }
                apply.configure { it.dependsOn(compiles) }
            }
        }
    }

    /**
     * Adds the compiler-plugin args once the project is evaluated, so they land after a consumer's
     * own `compilerOptions.freeCompilerArgs.set(...)`; the property is final by the time a task
     * action runs. A kapt stub task inherits the real compile's arguments from the Kotlin Gradle
     * plugin and is switched off again by an `enabled=false` of its own.
     */
    private fun wireCompiles(
        project: Project,
        extension: WrasseExtension,
        wrasseDir: Provider<Directory>,
        compiles: TaskCollection<out Task>,
    ) {
        project.dependencies.addProvider(
            "kotlinCompilerPluginClasspath",
            extension.version.map { "com.varlanv.wrasse:compiler-plugin:$it" },
        )
        val configChain = project.provider { configChain(extension.configFile.orNull?.asFile) }
        compiles.configureEach { compile ->
            if (isKaptStub(compile)) return@configureEach
            compile.outputs
                .dir(wrasseDir.map { File(File(it.asFile, compilationName(compile.name)), PATCH_DIR) })
                .withPropertyName("wrassePatch")
            compile.inputs
                .files(configChain)
                .withPathSensitivity(PathSensitivity.NONE)
                .withPropertyName("wrasseConfig")
        }
        whenEvaluated(project) {
            if (extension.enabled.get() && extension.configFile.orNull == null) {
                throw GradleException(
                    "wrasse: no wrasse.json or wrasse.jsonc found walking up from ${project.projectDir}",
                )
            }
            requireCommaFree(project.projectDir.absolutePath)
            val buildDir = project.layout.buildDirectory.get().asFile
            val excludedRoot = realPath(buildDir)
            requireCommaFree(excludedRoot)
            compiles.configureEach { compile ->
                if (isKaptStub(compile)) {
                    appendFreeCompilerArgs(compile, listOf("-P", "plugin:$PLUGIN_ID:enabled=false"))
                    return@configureEach
                }
                val fixOutputDir = File(File(buildDir, WRASSE_DIR), compilationName(compile.name)).absolutePath
                appendFreeCompilerArgs(
                    compile,
                    listOf(
                        "-P", "plugin:$PLUGIN_ID:enabled=${extension.enabled.get()}",
                        "-P", "plugin:$PLUGIN_ID:warnOnly=${extension.warnOnly.get()}",
                        "-P", "plugin:$PLUGIN_ID:fixOutputDir=$fixOutputDir",
                        "-P", "plugin:$PLUGIN_ID:excludedRoot=$excludedRoot",
                        "-P", "plugin:$PLUGIN_ID:projectDir=${project.projectDir.absolutePath}",
                    ),
                )
            }
        }
    }
}

/**
 * Deletes the request files of this project's compilations when the build ends, whatever its
 * outcome, so a request no compile consumed cannot silence the next build.
 */
abstract class WrasseRequestCleanupService :
    BuildService<WrasseRequestCleanupService.Params>,
    OperationCompletionListener,
    AutoCloseable {
    interface Params : BuildServiceParameters {
        val compilationDirs: ListProperty<File>
    }

    override fun onFinish(event: FinishEvent) = Unit

    override fun close() {
        for (dir in parameters.compilationDirs.get()) File(dir, REQUEST_FILE).delete()
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

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @TaskAction
    fun apply() {
        val dir = wrasseDir.get().asFile
        if (!dir.isDirectory) return
        val root = projectDir.get().asFile
        deleteRequests(dir)
        val lines = when {
            hasEntries(dir, PATCH_FILE, "file:") -> applyAndReplay(dir, toolClasspath.files, root)
            hasEntries(dir, REPORT_FILE, "diag:") -> replay(dir, toolClasspath.files, root)
            else -> emptyList()
        }
        for (line in lines) logger.lifecycle(line)
        if (lines.any { it.startsWith("FAILED: ") }) {
            throw GradleException("wrasse could not apply every patch under ${dir.absolutePath}")
        }
    }
}

@UntrackedTask(because = "it prints findings recorded by compiles and must run every time")
abstract class WrasseLintTask : DefaultTask() {
    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val wrasseDir: DirectoryProperty

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Internal
    abstract val compileSources: ConfigurableFileCollection

    @get:Internal
    abstract val projectPath: Property<String>

    @TaskAction
    fun lint() {
        val dir = wrasseDir.get().asFile
        if (!dir.isDirectory) return
        deleteRequests(dir)
        if (!hasEntries(dir, REPORT_FILE, "diag:")) return
        val sources = compileSources.files.mapTo(HashSet()) { it.toPath().toUri().toString() }
        val lines = replay(dir, toolClasspath.files, projectDir.get().asFile).filter { reportedUri(it) in sources }
        for (line in lines) logger.lifecycle(line)
        val errors = lines.count { it.startsWith("e: ") }
        if (errors > 0) throw GradleException("wrasse found $errors error-level violation(s) in ${projectPath.get()}")
    }
}

private fun isCompileTaskName(taskName: String): Boolean = taskName.startsWith("compile") && taskName.contains("Kotlin")

private fun compilationName(taskName: String): String = when (taskName) {
    "compileKotlin" -> "main"
    "compileTestKotlin" -> "test"
    else -> taskName.removePrefix("compile").replaceFirstChar { it.lowercaseChar() }.ifEmpty { taskName }
}

private fun whenEvaluated(project: Project, action: () -> Unit) {
    if (project.state.executed) action() else project.afterEvaluate { action() }
}

private fun requireCommaFree(path: String) {
    if (path.contains(',')) {
        throw GradleException(
            "wrasse: the Kotlin compiler splits plugin options on ',', so wrasse cannot run from a path containing one: $path",
        )
    }
}

private fun realPath(file: File): String =
    runCatching { file.toPath().toRealPath().toString() }
        .recoverCatching { file.canonicalPath }
        .getOrDefault(file.absolutePath)

private fun discoverConfig(start: File): File? =
    generateSequence(start) { it.parentFile }
        .flatMap { dir -> sequenceOf(File(dir, "wrasse.json"), File(dir, "wrasse.jsonc")) }
        .firstOrNull { it.isFile }

private fun configChain(config: File?): List<File> {
    val chain = LinkedHashSet<File>()
    var next = config
    while (true) {
        val current = next ?: break
        if (!current.isFile || !chain.add(current)) break
        next = EXTENDS_ENTRY
            .find(current.readText())
            ?.groupValues
            ?.get(1)
            ?.let { File(current.parentFile, it).normalize() }
    }
    return chain.toList()
}

private fun kotlinCompileClass(project: Project, pluginId: String): Class<out Task>? {
    val plugin = project.plugins.findPlugin(pluginId) ?: return null
    return runCatching { plugin.javaClass.classLoader.loadClass("org.jetbrains.kotlin.gradle.tasks.KotlinCompile") }
        .getOrNull()
        ?.asSubclass(Task::class.java)
}

private fun isKaptStub(task: Task): Boolean =
    generateSequence<Class<*>>(task.javaClass) { it.superclass }.any { it.name == KAPT_STUB_TASK_CLASS }

/**
 * Appends [args] to a Kotlin compile task's `compilerOptions.freeCompilerArgs` without dropping a
 * value the Kotlin Gradle plugin only conventions onto it — which `addAll` does, leaving a kapt
 * stub task, whose arguments are that convention, with no value at all.
 */
private fun appendFreeCompilerArgs(task: Task, args: List<String>) {
    val property = freeCompilerArgs(task)
    val append = runCatching { property.javaClass.getMethod("appendAll", Iterable::class.java) }.getOrNull()
    if (append == null) property.addAll(args) else append.invoke(property, args)
}

@Suppress("UNCHECKED_CAST")
private fun freeCompilerArgs(task: Task): ListProperty<String> {
    val options = task.javaClass.getMethod("getCompilerOptions").invoke(task)
    return options.javaClass.getMethod("getFreeCompilerArgs").invoke(options) as ListProperty<String>
}

private fun sourcesOf(task: Task): FileCollection = task.javaClass.getMethod("getSources").invoke(task) as FileCollection

private fun hasEntries(dir: File, fileName: String, linePrefix: String): Boolean =
    dir.walkTopDown().any { f -> f.isFile && f.name == fileName && f.useLines { lines -> lines.any { it.startsWith(linePrefix) } } }

private fun deleteRequests(dir: File) {
    dir.walkTopDown().filter { it.isFile && it.name == REQUEST_FILE }.forEach { it.delete() }
}

private fun reportedUri(line: String): String {
    val start = line.indexOf(": ")
    val end = line.indexOf(" wrasse: ")
    if (start < 0 || end <= start) return ""
    return line.substring(start + 2, end).substringBeforeLast(':').substringBeforeLast(':')
}

@Suppress("UNCHECKED_CAST")
private fun replay(dir: File, classpath: Set<File>, projectDir: File): List<String> =
    withTool(classpath, "WReportReplayKt", "replayReports", dir, projectDir) as List<String>

@Suppress("UNCHECKED_CAST")
private fun applyAndReplay(dir: File, classpath: Set<File>, projectDir: File): List<String> =
    withTool(classpath, "WPatchApplierKt", "applyAndReplay", dir, projectDir) as List<String>

private fun withTool(classpath: Set<File>, className: String, method: String, dir: File, projectDir: File): Any {
    val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
    return URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
        Class.forName("com.varlanv.wrasse.lang.$className", true, loader)
            .getMethod(method, List::class.java, Path::class.java)
            .invoke(null, listOf(dir.absolutePath), projectDir.toPath())
    }
}
