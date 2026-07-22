package sample

fun foo(i: Int) {
    do {
        if (i > 2) break else continue
    } while (i < 1)
}

// expect-error 4:5 loop-with-too-many-jump-statements "The loop contains more than one break or continue statement (found 2); the code should be refactored to increase readability"
