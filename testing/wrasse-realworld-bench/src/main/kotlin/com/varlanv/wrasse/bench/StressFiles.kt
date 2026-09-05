package com.varlanv.wrasse.bench

/** Pathological shapes a large real codebase contains somewhere: generated giants, deep nesting, huge literals. */
object StressFiles {
    private const val PKG = "bench.gen.stress"

    fun generate(spec: StressSpec): List<KotlinSourceGen.GeneratedFile> {
        val files = mutableListOf<KotlinSourceGen.GeneratedFile>()
        if (spec.hugeFileFunctions > 0) files.add(hugeFile(spec.hugeFileFunctions))
        if (spec.longFunctionStatements > 0) files.add(longFunction(spec.longFunctionStatements))
        if (spec.nestingDepth > 0) files.add(deepNesting(spec.nestingDepth))
        if (spec.longLineChars > 0) files.add(longLine(spec.longLineChars))
        if (spec.rawStringLines > 0) files.add(rawString(spec.rawStringLines))
        return files
    }

    private fun file(
        name: String,
        body: String,
    ) = KotlinSourceGen.GeneratedFile("src/main/kotlin/${PKG.replace('.', '/')}/$name.kt", "package $PKG\n\n$body")

    private fun hugeFile(functions: Int): KotlinSourceGen.GeneratedFile {
        val sb = StringBuilder()
        sb.append("object Generated {\n")
        for (n in 0 until functions) {
            sb.append("    fun entry$n(input: Int): Int {\n")
            sb.append("        val doubled = input * 2 + $n\n")
            sb.append("        return if (doubled % 2 == 0) doubled else doubled - 1\n")
            sb.append("    }\n\n")
        }
        sb.append("    fun sumAll(input: Int): Long {\n        var total = 0L\n")
        for (n in 0 until functions step 50) sb.append("        total += entry$n(input)\n")
        sb.append("        return total\n    }\n}\n")
        return file("Generated", sb.toString())
    }

    private fun longFunction(statements: Int): KotlinSourceGen.GeneratedFile {
        val sb = StringBuilder()
        sb.append("fun accumulate(seed: Int): Long {\n    var acc = seed.toLong()\n")
        for (n in 0 until statements) sb.append("    acc = acc * 31 + ${n % 97}\n")
        sb.append("    return acc\n}\n")
        return file("LongFunction", sb.toString())
    }

    private fun deepNesting(depth: Int): KotlinSourceGen.GeneratedFile {
        val sb = StringBuilder()
        sb.append("fun deep(x: Int): Int {\n")
        for (d in 0 until depth) sb.append("    ".repeat(d + 1)).append("if (x > $d) {\n")
        sb.append("    ".repeat(depth + 1)).append("return x\n")
        for (d in depth - 1 downTo 0) sb.append("    ".repeat(d + 1)).append("}\n")
        sb.append("    return -x\n}\n")
        return file("DeepNesting", sb.toString())
    }

    private fun longLine(chars: Int): KotlinSourceGen.GeneratedFile {
        val parts = (0 until chars / 12).joinToString(" + ") { "\"seg$it\"" }
        return file("LongLine", "val longLine: String = $parts\n")
    }

    private fun rawString(lines: Int): KotlinSourceGen.GeneratedFile {
        val sb = StringBuilder()
        sb.append("val bigTemplate: String = \"\"\"\n")
        for (n in 0 until lines) sb.append("    line $n of the embedded document with some words in it\n")
        sb.append("\"\"\".trimIndent()\n")
        return file("BigTemplate", sb.toString())
    }
}
