package sample

fun foo(b: Int) {
    for (i in 1..(b - 1)) print(i)
}

// expect-error 4:15 range-conventional "Replace .. with until"
