package sample

fun foo(items: List<Int>) {
    for (item in items) {
        if (item == 1) {
            continue
        }
        if (item == 2) {
            break
        }
    }
}

// expect-error 4:5 loop-with-too-many-jump-statements "The loop contains 2 break or continue statements; the maximum allowed is 1"
