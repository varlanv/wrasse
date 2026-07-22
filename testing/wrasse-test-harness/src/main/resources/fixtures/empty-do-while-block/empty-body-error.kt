package sample

fun foo(x: Boolean) {
    do {
    } while (x)
}

// expect-error 4:8 empty-do-while-block "Empty do-while block detected. Empty blocks of code serve no purpose and should be removed"
