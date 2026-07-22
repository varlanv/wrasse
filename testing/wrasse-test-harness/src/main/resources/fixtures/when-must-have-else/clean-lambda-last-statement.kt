package sample

fun foo(flag: Boolean) {
    val block = {
        when (flag) {
            true -> println("yes")
            false -> println("no")
        }
    }
    block()
}

// expect-clean
