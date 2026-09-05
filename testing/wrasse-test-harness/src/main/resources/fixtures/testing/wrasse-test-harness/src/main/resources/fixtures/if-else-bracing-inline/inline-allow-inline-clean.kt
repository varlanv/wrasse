package sample

fun pick(flag: Boolean): String {
    val a = if (flag) "1" else "2"
    if (flag) return a
    return a + a
}

// fixture-config: {"rules":{"if-else-bracing":{"level":"error","allow-inline":true}}}
// expect-clean
