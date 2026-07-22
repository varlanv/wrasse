package sample

fun foo(i: Int) {
    while (i < 3) {
        if (i > 1) break else continue
    }
}

// expect-error 4:5 loop-with-too-many-jump-statements "The loop contains more than one break or continue statement (found 2); the code should be refactored to increase readability"
