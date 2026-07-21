package sample

fun one() {}
fun two() {}

fun example() {
    if (true) { one(); two() }
}

// expect-error 1:1 format "File is not wrasse-formatted"
