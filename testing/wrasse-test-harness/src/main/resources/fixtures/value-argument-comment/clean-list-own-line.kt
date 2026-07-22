package sample

fun foo(bar: Int) = bar

val result = foo(
    // raw
    bar = 1,
)

// expect-clean
