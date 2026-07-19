package com.varlanv.wrasse.benchmarks

import kotlin.random.Random

/**
 * Deterministic synthetic corpus for the walk-throughput tripwire (design.md §9) — replaces the
 * old "concatenate this repo's own `.kt` sources" corpus, which grew every session and made
 * cross-session comparison meaningless. Same [SEED] + same [CORPUS_VERSION] always produces the
 * same [Corpus]: no filesystem read, no dependency on anything outside this object.
 *
 * [CORPUS_VERSION] is part of the benchmark contract — bump it whenever [generateFile]'s output
 * shape changes, so a historical comparison against an older run is knowingly invalidated rather
 * than silently misleading.
 */
object BenchmarkCorpusGenerator {

    const val CORPUS_VERSION = 1

    private const val SEED = 20260719L
    private const val FILE_COUNT = 100

    class GeneratedFile(val fileName: String, val content: String)

    class Corpus(val files: List<GeneratedFile>) {
        val fileCount: Int = files.size
        val totalBytes: Long = files.sumOf { it.content.length.toLong() }
    }

    private val explicitImportPool = listOf(
        "com.varlanv.wrasse.bench.fixtures.alpha.Alpha",
        "com.varlanv.wrasse.bench.fixtures.alpha.Beta",
        "com.varlanv.wrasse.bench.fixtures.beta.Gamma",
        "com.varlanv.wrasse.bench.fixtures.beta.Delta",
        "com.varlanv.wrasse.bench.fixtures.gamma.Epsilon",
        "com.varlanv.wrasse.bench.fixtures.gamma.Zeta",
        "com.varlanv.wrasse.bench.fixtures.delta.Eta",
        "com.varlanv.wrasse.bench.fixtures.delta.Theta",
        "com.varlanv.wrasse.bench.fixtures.epsilon.Iota",
        "com.varlanv.wrasse.bench.fixtures.epsilon.Kappa",
        "com.varlanv.wrasse.bench.fixtures.zeta.Lambda",
        "com.varlanv.wrasse.bench.fixtures.zeta.Mu",
    )

    private val starImportPool = listOf(
        "com.varlanv.wrasse.bench.fixtures.collections",
        "com.varlanv.wrasse.bench.fixtures.util",
    )

    private val classNamePool = listOf(
        "Widget", "Processor", "Registry", "Handler", "Builder",
        "Manager", "Coordinator", "Resolver", "Adapter", "Pipeline",
    )

    fun generate(fileCount: Int = FILE_COUNT): Corpus {
        val random = Random(SEED)
        return Corpus((0 until fileCount).map { index -> generateFile(index, random) })
    }

    private fun generateFile(index: Int, random: Random): GeneratedFile {
        val packageName = "com.varlanv.wrasse.benchmarks.corpus.f$index"
        val className = "${classNamePool[index % classNamePool.size]}$index"
        val importCount = 3 + (index % 4)
        val explicitImports = explicitImportPool.shuffled(random).take(importCount)
        val importMode = index % 3

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        appendImports(sb, importMode, explicitImports)
        sb.appendLine()
        appendClass(sb, index, className)
        sb.appendLine()
        sb.appendLine("private fun topLevelHelper$index(x: Int): Int = x * $index + ${index % 7}")

        return GeneratedFile("Fixture$index.kt", sb.toString())
    }

    private fun appendImports(sb: StringBuilder, importMode: Int, explicitImports: List<String>) {
        val ordered = if (importMode == 0) explicitImports.sorted() else explicitImports
        for (importPath in ordered) {
            sb.appendLine("import $importPath")
        }
        if (importMode == 2) {
            for (starImport in starImportPool) {
                sb.appendLine("import $starImport.*")
            }
        }
    }

    private fun appendClass(sb: StringBuilder, index: Int, className: String) {
        sb.appendLine("/**")
        sb.appendLine(" * $className synthetic benchmark fixture #$index.")
        sb.appendLine(" *")
        sb.appendLine(" * Exercises nested declarations, control flow and string templates for the")
        sb.appendLine(" * walk-throughput tripwire (design.md §9).")
        sb.appendLine(" */")
        sb.appendLine("@Suppress(\"unused\", \"MemberVisibilityCanBePrivate\")")
        sb.appendLine("class $className(private val seed: Int) {")
        sb.appendLine()
        sb.appendLine("    /** Nested configuration holder. */")
        sb.appendLine("    class Config(val label: String, val weight: Int)")
        sb.appendLine()
        sb.appendLine("    enum class Mode { FAST, SLOW, AUTO, IDLE }")
        sb.appendLine()
        sb.appendLine("    companion object {")
        sb.appendLine("        const val DEFAULT_LABEL = \"$className-default\"")
        sb.appendLine()
        sb.appendLine("        @JvmStatic")
        sb.appendLine("        fun create(seed: Int): $className = $className(seed)")
        sb.appendLine("    }")
        sb.appendLine()
        appendComputeFunction(sb, className)
        sb.appendLine()
        appendDescribeFunction(sb)
        sb.appendLine()
        appendConfigsFunction(sb, className)
        sb.appendLine("}")
    }

    private fun appendComputeFunction(sb: StringBuilder, className: String) {
        sb.appendLine("    /**")
        sb.appendLine("     * Combines [a] and [b] using a small mix of operators and control flow.")
        sb.appendLine("     */")
        sb.appendLine("    fun compute(a: Int, b: Int): Int {")
        sb.appendLine("        val sum = a + b - seed")
        sb.appendLine("        val label = \"sum=\$sum;pair=(\$a,\$b);owner=$className\"")
        sb.appendLine("        val double = { x: Int -> x * 2 + seed }")
        sb.appendLine("        val result = when {")
        sb.appendLine("            sum > 10 -> double(sum)")
        sb.appendLine("            sum == 0 -> 0")
        sb.appendLine("            else -> double(-sum)")
        sb.appendLine("        }")
        sb.appendLine("        var total = 0")
        sb.appendLine("        for (i in 0 until 5) {")
        sb.appendLine("            total += if (i % 2 == 0) i else -i")
        sb.appendLine("        }")
        sb.appendLine("        return result + total + label.length")
        sb.appendLine("    }")
    }

    private fun appendDescribeFunction(sb: StringBuilder) {
        sb.appendLine("    @Deprecated(\"Use describe(Mode) call sites directly\", ReplaceWith(\"describe(mode)\"))")
        sb.appendLine("    fun legacyDescribe(mode: Mode): String = describe(mode)")
        sb.appendLine()
        sb.appendLine("    fun describe(mode: Mode): String {")
        sb.appendLine("        // EOL comment exercising the comment-spacing/comment-span vocabulary")
        sb.appendLine("        return when (mode) {")
        sb.appendLine("            Mode.FAST -> \"fast:\$seed\"")
        sb.appendLine("            Mode.SLOW -> \"slow:\$seed\"")
        sb.appendLine("            Mode.AUTO -> \"auto:\$seed\"")
        sb.appendLine("            Mode.IDLE -> \"idle:\$seed\"")
        sb.appendLine("        }")
        sb.appendLine("    }")
    }

    private fun appendConfigsFunction(sb: StringBuilder, className: String) {
        sb.appendLine("    fun configs(): List<Config> {")
        sb.appendLine("        /* block comment above a builder-ish call chain */")
        sb.appendLine("        return listOf(")
        sb.appendLine("            Config(\"$className-a\", seed),")
        sb.appendLine("            Config(\"$className-b\", seed * 2),")
        sb.appendLine("        ).filter { it.weight >= 0 }")
        sb.appendLine("    }")
    }
}
