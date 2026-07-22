package sample

fun foo(x: Boolean) {
    if (x) {
    }
}

// expect-error 4:12 empty-if-block "This if block is empty and can be removed"
