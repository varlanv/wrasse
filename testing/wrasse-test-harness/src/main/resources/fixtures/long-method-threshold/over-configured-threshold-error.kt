package sample

fun f() {
    val a = 1
    val b = 2
    val c = 3
    val d = 4
}

// expect-error 3:5 long-method "Function 'f' is too long (6 lines); the maximum allowed is 5"
