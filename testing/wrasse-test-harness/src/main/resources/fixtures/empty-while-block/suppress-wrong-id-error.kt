package sample

@Suppress("no-such-rule")
fun foo(x: Boolean) {
    while (x) {
    }
}

// expect-error 5:15 empty-while-block "Empty while block detected. Empty blocks of code serve no purpose and should be removed"
