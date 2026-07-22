package sample

@Suppress("no-such-rule")
fun foo(items: List<Int>) {
    for (item in items) {
        if (item == 1) {
            break
        }
        if (item == 2) {
            continue
        }
    }
}

// expect-error 5:5 loop-with-too-many-jump-statements "The loop contains more than one break or continue statement (found 2); the code should be refactored to increase readability"
