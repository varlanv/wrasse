package sample

@Suppress("no-such-rule")
fun foo(x: Boolean) {
    if (x) {
    }
}

// expect-error 5:12 empty-if-block "This if block is empty and can be removed"
