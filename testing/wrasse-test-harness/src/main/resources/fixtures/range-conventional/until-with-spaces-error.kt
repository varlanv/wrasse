package sample

fun foo() {
    for (i in 1 .. (4 - 1)) print(i)
}

// expect-error 4:15 range-conventional "Replace .. with until"
