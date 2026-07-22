package sample

fun foo(items: List<Int>) {
    for (item in items) {
    }
}

// expect-error 4:25 empty-for-block "Empty for block detected. Empty blocks of code serve no purpose and should be removed"
