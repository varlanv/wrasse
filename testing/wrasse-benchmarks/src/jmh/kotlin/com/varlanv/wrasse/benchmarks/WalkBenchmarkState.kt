@file:OptIn(org.jetbrains.kotlin.K1Deprecation::class, org.jetbrains.kotlin.config.CompilerConfiguration.Internals::class)

package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.format.DocBuilder
import com.varlanv.wrasse.model.FormatStyle
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.rules.BooleanExpressionsRule
import com.varlanv.wrasse.rules.ClassNamingRule
import com.varlanv.wrasse.rules.CollapseIfRule
import com.varlanv.wrasse.rules.EqualsNullCallRule
import com.varlanv.wrasse.rules.FunctionExpressionBodyRule
import com.varlanv.wrasse.rules.ImportEngine
import com.varlanv.wrasse.rules.MayBeConstantRule
import com.varlanv.wrasse.rules.NoEmptyParensBeforeTrailingLambdaRule
import com.varlanv.wrasse.rules.NoSemicolonsRule
import com.varlanv.wrasse.rules.PropertyNamingRule
import com.varlanv.wrasse.rules.RangeConventionalRule
import com.varlanv.wrasse.rules.RedundantToStringInTemplateRule
import com.varlanv.wrasse.rules.TrailingNewlineRule
import com.varlanv.wrasse.rules.UseLetRule
import com.varlanv.wrasse.rules.WhenEntryBracingRule
import org.jetbrains.kotlin.KtInMemoryTextSourceFile
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

class CorpusSource(val fileName: String, val lightSource: KtLightSourceElement)

@State(Scope.Benchmark)
open class WalkBenchmarkState {

    lateinit var corpus: List<CorpusSource>
    private lateinit var disposable: Disposable
    private lateinit var uninitializedRules: List<WUninitializedRule>
    private lateinit var bufferedRules: List<WUninitializedRule>

    @Setup(Level.Trial)
    fun setUp() {
        setupIdeaStandaloneExecution()
        disposable = Disposer.newDisposable()
        val environment = KotlinCoreEnvironment.createForParallelTests(
            disposable,
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        val generated = BenchmarkCorpusGenerator.generate()
        println(
            "wrasse-benchmarks: corpus version=${BenchmarkCorpusGenerator.CORPUS_VERSION} " +
                "files=${generated.fileCount} totalBytes=${generated.totalBytes} tree=light project=${environment.project.name}",
        )
        corpus = generated.files.map { file ->
            val tree = KotlinLightParser.buildLightTree(
                file.content,
                KtInMemoryTextSourceFile(file.fileName, file.fileName, file.content),
            ) { _, _, message -> error("benchmark corpus does not parse: ${file.fileName}: $message") }
            val root = tree.root
            CorpusSource(
                fileName = file.fileName,
                lightSource = KtLightSourceElement(root, root.startOffset, root.endOffset, tree),
            )
        }
        uninitializedRules = listOf(NoSemicolonsRule(), TrailingNewlineRule())
        bufferedRules = listOf(
            BooleanExpressionsRule(),
            ClassNamingRule(),
            CollapseIfRule(),
            EqualsNullCallRule(),
            FunctionExpressionBodyRule(),
            MayBeConstantRule(),
            NoEmptyParensBeforeTrailingLambdaRule(),
            PropertyNamingRule(),
            RangeConventionalRule(),
            RedundantToStringInTemplateRule(),
            UseLetRule(),
            WhenEntryBracingRule(),
        )
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        val application = ApplicationManager.getApplication()
        if (application != null) {
            application.runWriteAction { Disposer.dispose(disposable) }
        } else {
            Disposer.dispose(disposable)
        }
    }

    fun shippedRuleDispatch(): StreamDispatch {
        val config = WrasseRuleConfig(RuleLevel.ERROR, emptyList(), RuleLevel.ERROR)
        val engine = ImportEngine()
        val rules = uninitializedRules.map { it.initRule(config) } +
            engine.initGroup(engine.ids.associateWith { config })
        return StreamDispatch(rules)
    }

    fun bufferedRuleDispatch(): StreamDispatch {
        val config = WrasseRuleConfig(RuleLevel.ERROR, emptyList(), RuleLevel.ERROR)
        return StreamDispatch(uninitializedRules.map { it.initRule(config) } + bufferedRules.map { it.initRule(config) })
    }

    fun formatDispatch(): StreamDispatch {
        val config = WrasseRuleConfig(RuleLevel.ERROR, emptyList(), RuleLevel.ERROR, formatEnabled = true)
        val formatConfig = WFormatConfig(enabled = true, style = FormatStyle(), ruleConfig = config)
        return StreamDispatch(uninitializedRules.map { it.initRule(config) } + DocBuilder(formatConfig))
    }

    fun noRuleDispatch(): StreamDispatch = StreamDispatch(emptyList())
}
