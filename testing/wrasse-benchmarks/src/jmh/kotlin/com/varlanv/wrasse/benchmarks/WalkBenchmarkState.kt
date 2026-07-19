@file:OptIn(org.jetbrains.kotlin.K1Deprecation::class, org.jetbrains.kotlin.config.CompilerConfiguration.Internals::class)

package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.rules.ImportEngine
import com.varlanv.wrasse.rules.NoSemicolonsRule
import com.varlanv.wrasse.rules.TrailingNewlineRule
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.toKtPsiSourceElement
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

@State(Scope.Benchmark)
open class WalkBenchmarkState {

    lateinit var lightSource: KtLightSourceElement
    private lateinit var disposable: Disposable
    private lateinit var uninitializedRules: List<WUninitializedRule>

    @Setup(Level.Trial)
    fun setUp() {
        setupIdeaStandaloneExecution()
        disposable = Disposer.newDisposable()
        val environment = KotlinCoreEnvironment.createForParallelTests(
            disposable,
            CompilerConfiguration(),
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        val psiFactory = KtPsiFactory(environment.project)
        val ktFile = psiFactory.createFile(BenchmarkCorpus.load())
        val psiSource = ktFile.toKtPsiSourceElement()
        lightSource = KtLightSourceElement(
            psiSource.lighterASTNode,
            psiSource.startOffset,
            psiSource.endOffset,
            psiSource.treeStructure,
        )
        uninitializedRules = listOf(NoSemicolonsRule(), TrailingNewlineRule())
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
        val rules = uninitializedRules.map { it.initRule(config) } +
            ImportEngine().initGroup(mapOf("no-wildcard-imports" to config))
        return StreamDispatch(rules)
    }

    fun noRuleDispatch(): StreamDispatch = StreamDispatch(emptyList())
}
