package sample

fun name(): Int {
    a(); return 1
}

fun a() {}

// expect-error 1:1 format "File is not wrasse-formatted"
