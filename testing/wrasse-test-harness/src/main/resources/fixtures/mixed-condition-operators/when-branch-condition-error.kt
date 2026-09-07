package sample

fun check(a: Boolean, b: Boolean, c: Boolean): Int = when {
    a && b || c -> 1
    else -> 2
}

// expect-error 4:5 mixed-condition-operators "A condition with mixed usage of '&&' and '||' is hard to read. Use parentheses to clarify the (sub)condition."
