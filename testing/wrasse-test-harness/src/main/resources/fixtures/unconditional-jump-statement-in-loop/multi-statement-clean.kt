package sample

fun foo() {
    for (i in 1..2) {
        println(i)
        break
    }
}

// expect-clean
