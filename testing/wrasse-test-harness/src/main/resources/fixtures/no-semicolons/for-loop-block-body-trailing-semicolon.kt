package sample

fun test() {
    for (i in 1..3) {
        touch(i);
    }
}

fun touch(i: Int) {}

// expect-error 5:17 no-semicolons "Unnecessary semicolon"
