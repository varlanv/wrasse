package sample

fun one() {}

fun example() {
    if (true) { one(); }
}

// expect-error 1:1 format "File is not wrasse-formatted"
