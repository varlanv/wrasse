package sample

class Compute(
    val a: Int, // keep this comment
    val b: Int
) {
    val sum = a + b


    val other = 1
}

// expect-error 1:1 format "File is not wrasse-formatted"
