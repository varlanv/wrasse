package sample

fun foo(i: Int) {
    while (i < 3) {
        if (i > 1) break else continue
    }
}

// expect-error 4:5 loop-with-too-many-jump-statements "The loop contains 2 break or continue statements; the maximum allowed is 1"
