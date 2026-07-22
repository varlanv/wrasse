package sample

fun foo(x: Int) {
    when (x) {
    }
}

// expect-error 4:5 empty-when-block "Empty when block detected. This when expression has no entries"
