package sample

fun foo(items: List<Int>) {
    for (item in items) {
        for (sub in items) {
            if (sub == 1) {
                break
            } else {
                continue
            }
        }
        break
    }
}

// expect-error 5:9 loop-with-too-many-jump-statements "The loop contains more than one break or continue statement (found 2); the code should be refactored to increase readability"
