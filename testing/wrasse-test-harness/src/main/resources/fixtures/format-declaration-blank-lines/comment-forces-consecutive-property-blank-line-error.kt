package sample

class Holder {
    val a = 1
    // comment before b
    val b = 2
}

// expect-error 1:1 format "File is not wrasse-formatted"
