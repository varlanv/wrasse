package sample

@Suppress("no-such-rule")
fun foo(x: Boolean) {
    do {
    } while (x)
}

// expect-error 5:8 empty-do-while-block "Empty do-while block detected. Empty blocks of code serve no purpose and should be removed"
