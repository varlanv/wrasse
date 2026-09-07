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

// expect-error 5:9 loop-with-too-many-jump-statements "The loop contains 2 break or continue statements; the maximum allowed is 1"
