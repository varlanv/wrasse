package sample

annotation class Ann1

fun foobar() {
    val bar = "bar"
    // Some comment
    @Ann1
    val foo = "foo"
    println(bar + foo)
}

// expect-clean
