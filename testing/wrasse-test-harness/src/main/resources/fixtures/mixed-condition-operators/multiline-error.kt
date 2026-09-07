package sample

fun check(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return a &&
        b ||
        c
}

// expect-error 4:12 mixed-condition-operators "A condition with mixed usage of '&&' and '||' is hard to read. Use parentheses to clarify the (sub)condition."
