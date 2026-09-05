package com.varlanv.wrasse.rules

/**
 * Per-function accumulator for the `FunctionMetricsEngine`'s five metrics (return-count,
 * throws-count, nested-block-depth, cyclomatic-complexity, long-method). One frame per active
 * `FUN`, pushed on enter and popped on exit; a nested function (local function or anonymous
 * function literal) gets its own independent frame and never contributes to an enclosing one —
 * nothing merges upward on pop. Pure counting logic, no kotlinc/AST dependency, so it is
 * unit-testable by simulating an event sequence directly.
 */
class FunctionMetricsFrame(
    var nameStart: Int,
    var nameEnd: Int,
    var functionName: String,
) {
    var returnCount = 0
        private set
    var throwCount = 0
        private set
    var complexity = 1
        private set
    var maxNestingDepth = 0
        private set
    var distinctCodeLines = 0
        private set

    private var nestingDepth = 0
    private var lastCodeLine = Int.MIN_VALUE

    fun recordReturn() {
        returnCount++
    }

    fun recordThrow() {
        throwCount++
    }

    fun addComplexity(amount: Int) {
        complexity += amount
    }

    fun enterNestingConstruct() {
        nestingDepth++
        if (nestingDepth > maxNestingDepth) maxNestingDepth = nestingDepth
    }

    fun exitNestingConstruct() {
        nestingDepth--
    }

    fun recordCodeLine(line: Int) {
        if (line != lastCodeLine) {
            distinctCodeLines++
            lastCodeLine = line
        }
    }
}
