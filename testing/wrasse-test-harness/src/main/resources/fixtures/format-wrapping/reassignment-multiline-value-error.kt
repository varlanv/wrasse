package sample

fun example(cond: Boolean): Int {
    var x = 0
    x = if (cond) {
        1
    } else {
        2
    }
    return x
}

// expect-error 1:1 format "File is not wrasse-formatted"
