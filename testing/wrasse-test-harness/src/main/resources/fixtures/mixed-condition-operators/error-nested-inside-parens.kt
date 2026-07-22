package sample

fun check(a: Boolean, b: Boolean, c: Boolean, d: Boolean, e: Boolean): Boolean {
    return a && (b || c && d) && e
}

// expect-error 4:18 mixed-condition-operators "A condition with mixed usage of '&&' and '||' is hard to read. Use parentheses to clarify the (sub)condition."
