package sample

@Suppress("no-such-rule")
fun foo(x: Int) {
    when (x) {
    }
}

// expect-error 5:5 empty-when-block "Empty when block detected. This when expression has no entries"
