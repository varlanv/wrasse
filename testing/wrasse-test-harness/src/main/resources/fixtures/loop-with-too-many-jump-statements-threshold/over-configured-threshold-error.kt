package sample

fun f() {
    for (i in 1..3) {
        if (i == 2) break
    }
}

// expect-error 4:5 loop-with-too-many-jump-statements "The loop contains 1 break or continue statements; the maximum allowed is 0"
