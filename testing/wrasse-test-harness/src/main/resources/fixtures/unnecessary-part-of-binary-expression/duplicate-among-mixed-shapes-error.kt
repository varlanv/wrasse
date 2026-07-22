package sample

class Bar(val bar: Boolean)

fun bar() {
    val foo = true
    val baz = 10
    val bar = Bar(true)

    if (baz < 10 || foo || bar.bar || baz > 10 || baz < 10) {
        println("work")
    }
}

// expect-error 10:9 unnecessary-part-of-binary-expression "This binary expression repeats one of its own operands unnecessarily"
