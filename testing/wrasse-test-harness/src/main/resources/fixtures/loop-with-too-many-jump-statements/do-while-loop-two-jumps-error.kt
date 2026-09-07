package sample

fun foo(i: Int) {
    do {
        if (i > 2) break else continue
    } while (i < 1)
}

// expect-error 4:5 loop-with-too-many-jump-statements "The loop contains 2 break or continue statements; the maximum allowed is 1"
