package sample

infix fun Int.orElse(other: Int): Int = if (this == 0) other else this

fun demo(a: Int, b: Int): Int {
    return (a)  orElse  (b)
}

// expect-error 1:1 format "File is not wrasse-formatted"
