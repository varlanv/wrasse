package sample

fun foo(modifier: Int) {
    bar(modifier = 1)
}

fun bar(modifier: Int) {
    println(modifier)
}

// expect-clean
