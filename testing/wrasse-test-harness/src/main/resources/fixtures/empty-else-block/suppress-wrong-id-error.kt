package sample

@Suppress("no-such-rule")
fun foo(x: Boolean) {
    if (x) {
        println("work")
    } else {
    }
}

// expect-error 7:12 empty-else-block "This else block is empty and can be removed"
