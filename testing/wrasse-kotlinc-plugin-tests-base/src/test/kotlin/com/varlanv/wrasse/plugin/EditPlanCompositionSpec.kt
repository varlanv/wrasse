@file:OptIn(K1Deprecation::class, CompilerConfiguration.Internals::class)

package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WLeafRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.K1Deprecation
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

private fun offRuleConfig() = WrasseRuleConfig(RuleLevel.OFF, emptyList(), RuleLevel.OFF)

private fun parseToLightSource(source: String, disposable: Disposable): KtLightSourceElement {
    setupIdeaStandaloneExecution()
    val environment = KotlinCoreEnvironment
        .createForParallelTests(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
    val psiFactory = KtPsiFactory(environment.project)
    val ktFile = psiFactory.createFile(source)
    val psiSource = ktFile.toKtPsiSourceElement()
    return KtLightSourceElement(psiSource.lighterASTNode, psiSource.startOffset, psiSource.endOffset, psiSource.treeStructure)
}

private val environmentLock = Any()

private val kotlinCoreEnvironmentWarmup: Unit by lazy {
    runCatching { runWalkOnce("val warmup = 1", emptyList()) }
    Unit
}

private fun runWalk(source: String, rules: List<WRule>): WContext {
    kotlinCoreEnvironmentWarmup
    return runWalkOnce(source, rules)
}

private fun runWalkOnce(source: String, rules: List<WRule>): WContext = synchronized(environmentLock) {
    val disposable = Disposer.newDisposable()
    try {
        val lightSource = parseToLightSource(source, disposable)
        val ctx = WContext(filePath = "sample.kt")
        val dispatch = StreamDispatch(rules)
        val reporter = object : WReporter {
            override val reports = mutableListOf<ViolationReport>()

            override fun report(ruleId: String, message: String, startOffset: Int, endOffset: Int, rule: WRule, edits: List<WEdit>) {
                reports.add(ViolationReport(message, startOffset, endOffset, rule.config.effectiveLevel))
                for (edit in edits) {
                    ctx.editPlan.add(ruleId, edit)
                }
            }
        }
        LightTreeStreamAdapter.walk(source = lightSource, ctx = ctx, dispatch = dispatch, reporter = reporter)
        return ctx
    } finally {
        val application = ApplicationManager.getApplication()
        if (application != null) {
            application.runWriteAction { Disposer.dispose(disposable) }
        } else {
            Disposer.dispose(disposable)
        }
    }
}

private class DoubleFirstIntegerLiteralRule : WLeafRule {
    override val id = "test-inner-double-int"
    override val config = offRuleConfig()
    override val targetTypes: Set<WNodeType> = setOf(WNodeType.INTEGER_LITERAL)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        if (ctx.leafText.toString() == "1") {
            reporter
                .report(id, "doubled", ctx.startOffset, ctx.endOffset, this, edits = listOf(WEdit(ctx.startOffset, ctx.endOffset, "42")))
        }
    }
}

private class PadArgumentListRule : WBufferedNodeRule {
    override val id = "test-outer-pad-arg-list"
    override val config = offRuleConfig()
    override val targetTypes: Set<WNodeType> = setOf(WNodeType.VALUE_ARGUMENT_LIST)

    var composedReplacement: String? = null
    var composedStart = -1
    var composedEnd = -1

    override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
        val spanStart = ctx.startOffset
        val spanEnd = ctx.endOffset
        val innerEdits = ctx.editPlan.takeEditsIn(spanStart, spanEnd)

        val sb = StringBuilder(ctx.sourceText.subSequence(spanStart, spanEnd).toString())
        for (entry in innerEdits.sortedByDescending { it.edit.startOffset }) {
            val relStart = entry.edit.startOffset - spanStart
            val relEnd = entry.edit.endOffset - spanStart
            sb.replace(relStart, relEnd, entry.edit.replacement)
        }
        sb.insert(sb.length - 1, " ")
        sb.insert(1, " ")

        composedReplacement = sb.toString()
        composedStart = spanStart
        composedEnd = spanEnd
        reporter.report(id, "padded", spanStart, spanEnd, this, edits = listOf(WEdit(spanStart, spanEnd, sb.toString())))
    }
}

private class FixedSpanLeafRule(
    override val id: String,
    private val startOffset: Int,
    private val endOffset: Int,
    private val replacement: String,
) : WLeafRule {
    override val config = offRuleConfig()
    override val targetTypes: Set<WNodeType> = setOf(WNodeType.INTEGER_LITERAL)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        reporter.report(id, "overlap", startOffset, endOffset, this, edits = listOf(WEdit(startOffset, endOffset, replacement)))
    }
}

class EditPlanCompositionSpec :
    BaseSpec(
        {

            should("compose an outer edit from an already-consumed inner edit via the EditPlan") {
                val source = "fun main() {\n    foo(1, 2)\n}\n"
                val inner = DoubleFirstIntegerLiteralRule()
                val outer = PadArgumentListRule()

                val ctx = runWalk(source, listOf(inner, outer))

                val finalEdits = ctx.editPlan.finalEdits()
                finalEdits shouldHaveSize 1
                finalEdits[0].startOffset shouldBe outer.composedStart
                finalEdits[0].endOffset shouldBe outer.composedEnd
                finalEdits[0].replacement shouldBe "( 42, 2 )"

                val patched = source.substring(0, finalEdits[0].startOffset) +
                    finalEdits[0].replacement +
                    source.substring(finalEdits[0].endOffset)
                patched shouldBe "fun main() {\n    foo( 42, 2 )\n}\n"
                source.substring(outer.composedStart, outer.composedEnd) shouldBe "(1, 2)"
            }

            should("fail loudly with full rule attribution when two independent rules emit overlapping edits") {
                val source = "val x = 123456"
                val ruleA = FixedSpanLeafRule("overlap-rule-a", 8, 12, "AAAA")
                val ruleB = FixedSpanLeafRule("overlap-rule-b", 10, 14, "BBBB")

                val ctx = runWalk(source, listOf(ruleA, ruleB))

                val exception = shouldThrow<IllegalStateException> {
                    ctx.editPlan.finalEdits()
                }

                exception.message shouldBe
                    "EditPlan: overlapping edits from rule 'overlap-rule-a' (8..12 -> \"AAAA\") " +
                    "and rule 'overlap-rule-b' (10..14 -> \"BBBB\")"
            }
        },
    )
