package com.varlanv.wrasse.rules

/**
 * Per-declaration accumulator for the `ClassMetricsEngine`'s two metrics (too-many-functions,
 * large-class). One frame per active `CLASS`/`OBJECT_DECLARATION`, pushed on enter and popped on
 * exit; a nested class/object/interface/enum gets its own independent frame and never contributes
 * to an enclosing one. Pure counting logic, no kotlinc/AST dependency, unit-testable directly.
 */
class ClassMetricsFrame(
    var nameStart: Int,
    var nameEnd: Int,
    var declarationName: String,
    var kindLabel: String,
) {
    var functionCount = 0
        private set
    var distinctCodeLines = 0
        private set

    private var lastCodeLine = Int.MIN_VALUE

    fun recordFunction() {
        functionCount++
    }

    fun recordCodeLine(line: Int) {
        if (line != lastCodeLine) {
            distinctCodeLines++
            lastCodeLine = line
        }
    }
}
