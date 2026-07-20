package sample

val demo =
    if (condition)
    else {
        bar()
    }

val condition = false
fun bar() {}

// expect-clean
