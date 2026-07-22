package sample

@Suppress("no-semicolons")
fun check(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return a && b || c
}

// expect-error 5:12 mixed-condition-operators "A condition with mixed usage of '&&' and '||' is hard to read. Use parentheses to clarify the (sub)condition."
