@file:OptIn(K1Deprecation::class, CompilerConfiguration.Internals::class)

package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.testing.BaseSpec
import io.kotest.matchers.collections.shouldBeEmpty
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.toKtPsiSourceElement

private const val REPRESENTATIVE_SOURCE = """
@file:JvmName("Sample")

package sample

import kotlin.math.PI as PiAlias

/**
 * KDoc on typealias.
 * @param x sample
 */
typealias IntList = List<Int>

annotation class MyAnno(val value: String)

sealed interface Shape

data class Circle(val radius: Double) : Shape

enum class Color(val hex: String) {
    RED("#f00"),
    GREEN("#0f0");

    companion object {
        fun default() = RED
    }
}

@MyAnno("x")
class Sample<out T : Any> private constructor(private val items: MutableList<T>) {

    init {
        println("init")
    }

    constructor() : this(mutableListOf())

    val lazy: String by lazy { "lazy" }

    var customProp: Int = 0
        get() = field + 1
        set(value) {
            field = value
        }

    inline fun <reified R> cast(): R? = items.firstOrNull() as? R

    suspend fun doWork(vararg xs: Int): Int {
        var total = 0
        for (x in xs) {
            total += x
        }
        return total
    }

    operator fun plus(other: Sample<T>): Sample<T> = this

    infix fun combine(other: Sample<T>): Sample<T> = this

    fun classify(x: Any?): String = when (x) {
        null -> "null"
        is Int -> "int"
        in 1..10 -> "range"
        else -> "other"
    }

    fun branch(flag: Boolean) {
        if (flag) println("yes") else println("no")
    }

    fun loops() {
        var i = 0
        while (i < 3) {
            i++
        }
        do {
            i--
        } while (i > 0)
        outer@ for (j in 0..2) {
            if (j == 1) continue@outer
            if (j == 2) break@outer
        }
    }

    fun tryIt(): Int = try {
        1
    } catch (e: Exception) {
        2
    } finally {
        println("done")
    }

    fun destructure() {
        val (a, b) = Pair(1, 2)
        val c = 'x'
        val d = 1.5f
        println("${'$'}a ${'$'}b ${'$'}{a + b} ${'$'}c ${'$'}d")
    }

    fun annotatedExpression(): Int = @Suppress("unchecked_cast") 1

    fun lambdas(block: (Int) -> Unit) {
        block(1)
        val f: (Int, Int) -> Int = { x, y -> x + y }
        listOf(1, 2).map { it * 2 }
        run label@{
            return@label
        }
    }

    fun nullables(s: String?): Int {
        val len = s?.length ?: 0
        return s!!.length + len
    }

    fun String.extFn(): Int = this.length

    fun spread(arr: IntArray) {
        doWork(*arr)
    }

    override fun toString(): String = "Sample"
}

fun main() {
    val s = Sample<Int>()
    println(s)
}
"""

open class WNodeTypeMappingCompletenessSpec : BaseSpec({
    should("map every node type encountered walking a representative Kotlin source") {
        val disposable = Disposer.newDisposable()
        try {
            setupIdeaStandaloneExecution()
            val environment = KotlinCoreEnvironment.createForParallelTests(
                disposable,
                CompilerConfiguration(),
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            val psiFactory = KtPsiFactory(environment.project)
            val ktFile = psiFactory.createFile(REPRESENTATIVE_SOURCE)
            val psiSource = ktFile.toKtPsiSourceElement()
            val lightSource = KtLightSourceElement(
                psiSource.lighterASTNode,
                psiSource.startOffset,
                psiSource.endOffset,
                psiSource.treeStructure,
            )

            val unknownOffsets = mutableListOf<Int>()
            val collector = UnknownCollectorRule(unknownOffsets)
            val dispatch = StreamDispatch(listOf(collector))
            val reporter = object : WReporter {
                override val reports = mutableListOf<ViolationReport>()
                override fun report(
                    ruleId: String,
                    message: String,
                    startOffset: Int,
                    endOffset: Int,
                    rule: WRule,
                    edits: List<WEdit>,
                ) {
                }
            }

            LightTreeStreamAdapter.walk(
                source = lightSource,
                ctx = WContext(filePath = "representative.kt"),
                dispatch = dispatch,
                reporter = reporter,
            )

            val kdocMarkdownInternalsAllowlistStart = REPRESENTATIVE_SOURCE.indexOf("/**")
            val kdocMarkdownInternalsAllowlistEnd = REPRESENTATIVE_SOURCE.indexOf("*/") + 2
            val kdocMarkdownInternalsAllowlist = kdocMarkdownInternalsAllowlistStart until kdocMarkdownInternalsAllowlistEnd
            val unexpectedUnknownOffsets = unknownOffsets.filterNot { it in kdocMarkdownInternalsAllowlist }

            unexpectedUnknownOffsets.shouldBeEmpty()
        } finally {
            val application = ApplicationManager.getApplication()
            if (application != null) {
                application.runWriteAction { Disposer.dispose(disposable) }
            } else {
                Disposer.dispose(disposable)
            }
        }
    }
})

private class UnknownCollectorRule(private val unknownOffsets: MutableList<Int>) : WStreamRule {
    override val id = "mapping-completeness-collector"
    override val config = WrasseRuleConfig(RuleLevel.OFF, emptyList(), RuleLevel.OFF)

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        recordIfUnknown(ctx)
    }

    override fun enterNode(ctx: WContext) {
        val isSyntheticPsiParseRoot = ctx.ancestors.isEmpty
        if (isSyntheticPsiParseRoot) {
            return
        }
        recordIfUnknown(ctx)
    }

    private fun recordIfUnknown(ctx: WContext) {
        if (ctx.type == WNodeType.UNKNOWN) {
            unknownOffsets.add(ctx.startOffset)
        }
    }
}
