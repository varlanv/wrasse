package com.varlanv.wrasse.bench

import kotlin.random.Random

/**
 * Deterministic generator of compilable Kotlin files shaped like application code: models,
 * sealed hierarchies, services calling each other across packages, extension utilities, suspend
 * functions, collection pipelines, string templates, nullable handling. A seeded share of files
 * carries formatting violations (spacing, indentation, trailing commas, import order and
 * wildcards, unused imports, semicolons, long lines, inline `if`, blank-line runs, unnecessary
 * qualified names), so a cold format has real work everywhere.
 */
class KotlinSourceGen(
    seed: Long,
    private val fileCount: Int,
    private val filesPerPackage: Int = 10,
) {
    private val random = Random(seed)

    class GeneratedFile(val relativePath: String, val content: String)

    fun packageOf(index: Int): String = "bench.gen.p${index / filesPerPackage}"

    fun generate(index: Int): GeneratedFile {
        val pkg = packageOf(index)
        val violations = if (random.nextInt(100) < 60) Violations.pick(random) else Violations.NONE
        val other = otherIndex(index)
        val kindOther = otherIndex(index)
        val body = StringBuilder()
        body.append("package $pkg\n\n")
        body.append(imports(index, other, kindOther, violations))
        body.append(modelSection(index, violations)).append('\n')
        body.append(sealedSection(index, violations)).append('\n')
        body.append(serviceSection(index, other, kindOther, violations)).append('\n')
        body.append(utilitySection(index, violations))
        var text = body.toString()
        if (violations.blankLineRuns) text = text.replace("}\n\nclass ", "}\n\n\n\nclass ")
        text = if (violations.missingFinalNewline) text.trimEnd('\n') else text.trimEnd('\n') + "\n"
        return GeneratedFile("src/main/kotlin/${pkg.replace('.', '/')}/File$index.kt", text)
    }

    private fun otherIndex(index: Int): Int {
        if (fileCount < 2) return index
        var other = random.nextInt(fileCount)
        if (other == index) other = (other + 1) % fileCount
        return other
    }

    private fun imports(
        index: Int,
        other: Int,
        kindOther: Int,
        v: Violations,
    ): String {
        val lines = mutableListOf(
            "import ${packageOf(other)}.Model$other",
            "import ${packageOf(other)}.Service$other",
            "import ${packageOf(kindOther)}.Kind$kindOther",
            "import kotlin.math.abs",
        )
        if (v.unusedImport) {
            val unused = otherIndex(index)
            lines.add("import ${packageOf(unused)}.Event$unused")
        }
        if (v.wildcardImport) lines.add("import ${packageOf(otherIndex(index))}.*")
        val ordered = if (v.unsortedImports) lines.reversed() else lines.sorted()
        return ordered.distinct().joinToString("\n") + "\n\n"
    }

    private fun modelSection(i: Int, v: Violations): String {
        val sp = if (v.spacing) "" else " "
        val comma = if (v.missingTrailingComma) "" else ","
        return """
            |enum class Kind$i {
            |    PRIMARY,
            |    SECONDARY,
            |    ARCHIVED$comma
            |}
            |
            |data class Model$i(
            |    val id: Long,
            |    val name: String,
            |    val kind: Kind$i,
            |    val score: Double,
            |    val tags: List<String> = emptyList()$comma
            |) {
            |    val label: String
            |        get() = "${'$'}name#${'$'}id"
            |
            |    fun isActive(): Boolean = kind != Kind$i.ARCHIVED && score$sp>${sp}0.0
            |
            |    fun withScore(delta: Double): Model$i = copy(score = score + delta)
            |
            |    companion object {
            |        const val MAX_TAGS = 8
            |
            |        fun empty(id: Long): Model$i = Model$i(id, "model-${'$'}id", Kind$i.PRIMARY, 0.0)
            |    }
            |}
            |
            """
            .trimMargin()
    }

    private fun sealedSection(i: Int, v: Violations): String {
        val sp = if (v.spacing) "" else " "
        val inlineIf =
            if (v.inlineIf) "if (amount > 0) amount else 0.0" else "if (amount > 0) {\n            amount\n        } else {\n            0.0\n        }"
        return """
            |sealed class Event$i {
            |    abstract val at: Long
            |
            |    data class Created$i(override val at: Long, val model: Model$i) : Event$i()
            |
            |    data class Scored$i(override val at: Long, val amount: Double) : Event$i()
            |
            |    data class Removed$i(override val at: Long, val reason: String) : Event$i()
            |}
            |
            |fun describe$i(event: Event$i): String = when (event) {
            |    is Event$i.Created$i -> "created ${'$'}{event.model.label}"
            |    is Event$i.Scored$i -> "scored by ${'$'}{event.amount}"
            |    is Event$i.Removed$i -> "removed: ${'$'}{event.reason}"
            |}
            |
            |fun credit$i(amount: Double): Double {
            |    val safe$sp=$sp$inlineIf
            |    return abs(safe)
            |}
            |
            """
            .trimMargin()
    }

    private fun serviceSection(
        i: Int,
        other: Int,
        kindOther: Int,
        v: Violations,
    ): String {
        val indent = if (v.twoSpaceIndent) "  " else "    "
        val semi = if (v.semicolons) ";" else ""
        val comma = if (v.missingTrailingComma) "" else ","
        val longLine = if (v.longLine) {
            "        val summary = \"service-$i handled \" + events.size + \" events for \" + name + \" with total \" + total + \" and average \" + average + \" over \" + models.size + \" models in kind \" + kind\n"
        } else {
            "        val summary = \"service-$i handled ${'$'}{events.size} events for ${'$'}name\"\n"
        }
        val fqn = if (v.unnecessaryFqn) "kotlin.collections.List<Model$i>" else "List<Model$i>"
        return """
            |class Service$i(
            |    private val name: String,
            |    private val other: Service$other?,
            |    private val kind: Kind$kindOther = Kind$kindOther.PRIMARY$comma
            |) {
            |    private val models = mutableListOf<Model$i>()
            |    private val events = ArrayList<Event$i>()
            |
            |    fun register(model: Model$i): Boolean {
            |${indent}${indent}if (model.tags.size > Model$i.MAX_TAGS) {
            |${indent}${indent}${indent}return false$semi
            |${indent}${indent}}
            |${indent}${indent}models.add(model)$semi
            |${indent}${indent}events.add(Event$i.Created$i(at = models.size.toLong(), model = model))$semi
            |${indent}${indent}return true$semi
            |    }
            |
            |    fun rescore(id: Long, delta: Double): Model$i? {
            |        val index = models.indexOfFirst { it.id == id }
            |        if (index < 0) return null
            |        val updated = models[index].withScore(credit$i(delta))
            |        models[index] = updated
            |        events.add(Event$i.Scored$i(at = events.size.toLong(), amount = delta))
            |        return updated
            |    }
            |
            |    fun remove(id: Long, reason: String) {
            |        val removed = models.removeIf { it.id == id }
            |        if (removed) {
            |            events.add(Event$i.Removed$i(events.size.toLong(), reason))
            |        }
            |    }
            |
            |    fun active(): $fqn = models.filter { it.isActive() }.sortedByDescending { it.score }
            |
            |    fun report(): String {
            |        val total = models.sumOf { it.score }
            |        val average = if (models.isEmpty()) 0.0 else total / models.size
            |$longLine
            |        val lines = events.map { describe$i(it) }
            |        return (listOf(summary) + lines).joinToString(separator = "\n")
            |    }
            |
            |    fun delegate(model: Model$other): String = other?.report() ?: "no delegate for ${'$'}{model.label}"
            |
            |    fun byKind(): Map<Kind$i, List<Model$i>> = models.groupBy { it.kind }
            |
            |    fun seed(count: Int) {
            |        for (n in 1..count) {
            |            register(Model$i(id = n.toLong(), name = "seed-${'$'}n", kind = if (n % 3 == 0) Kind$i.SECONDARY else Kind$i.PRIMARY, score = n * 1.5, tags = listOf("t${'$'}n")))
            |        }
            |    }
            |
            |    suspend fun refresh(loader: suspend (Long) -> Model$i?): Int {
            |        var refreshed = 0
            |        for (model in models.toList()) {
            |            val fresh = loader(model.id) ?: continue
            |            if (rescore(model.id, fresh.score - model.score) != null) refreshed++
            |        }
            |        return refreshed
            |    }
            |}
            |
            """
            .trimMargin()
    }

    private fun utilitySection(i: Int, v: Violations): String {
        val sp = if (v.spacing) "" else " "
        val braces =
            if (v.inlineIf) "if (this.isEmpty()) return emptyMap()" else "if (this.isEmpty()) {\n        return emptyMap()\n    }"
        return """
            |fun List<Model$i>.scoreboard(): Map<String, Double> {
            |    $braces
            |    return associate { it.label to it.score }
            |}
            |
            |inline fun <T> List<T>.firstOrDefault$i(default: T, predicate: (T) -> Boolean): T = firstOrNull(predicate) ?: default
            |
            |fun Model$i.tagged(vararg extra: String): Model$i = copy(tags = tags + extra.toList())
            |
            |fun parseKind$i(raw: String?): Kind$i {
            |    val normalized$sp=$sp(raw ?: return Kind$i.PRIMARY).trim().uppercase()
            |    return try {
            |        Kind$i.valueOf(normalized)
            |    } catch (e: IllegalArgumentException) {
            |        Kind$i.ARCHIVED
            |    }
            |}
            |
            |fun summarize$i(models: List<Model$i>, limit: Int = 3): String {
            |    val top = models.asSequence().filter { it.isActive() }.sortedByDescending { it.score }.take(limit).map { it.label }.toList()
            |    return when {
            |        top.isEmpty() -> "none"
            |        top.size == 1 -> top.single()
            |        else -> top.joinToString(prefix = "[", postfix = "]")
            |    }
            |}
            |
            |object Registry$i {
            |    private val services = HashMap<String, Service$i>()
            |
            |    fun obtain(name: String): Service$i = services.getOrPut(name) { Service$i(name = name, other = null) }
            |
            |    fun clear() {
            |        services.clear()
            |    }
            |}
            """
            .trimMargin()
    }
}

class Violations(
    val spacing: Boolean = false,
    val twoSpaceIndent: Boolean = false,
    val missingTrailingComma: Boolean = false,
    val unusedImport: Boolean = false,
    val wildcardImport: Boolean = false,
    val unsortedImports: Boolean = false,
    val semicolons: Boolean = false,
    val longLine: Boolean = false,
    val inlineIf: Boolean = false,
    val blankLineRuns: Boolean = false,
    val missingFinalNewline: Boolean = false,
    val unnecessaryFqn: Boolean = false,
) {
    companion object {
        val NONE = Violations()

        fun pick(random: Random): Violations = Violations(
            spacing = random.nextInt(3) == 0,
            twoSpaceIndent = random.nextInt(4) == 0,
            missingTrailingComma = random.nextInt(3) == 0,
            unusedImport = random.nextInt(3) == 0,
            wildcardImport = random.nextInt(5) == 0,
            unsortedImports = random.nextInt(3) == 0,
            semicolons = random.nextInt(4) == 0,
            longLine = random.nextInt(3) == 0,
            inlineIf = random.nextInt(3) == 0,
            blankLineRuns = random.nextInt(4) == 0,
            missingFinalNewline = random.nextInt(5) == 0,
            unnecessaryFqn = random.nextInt(4) == 0,
        )
    }
}
