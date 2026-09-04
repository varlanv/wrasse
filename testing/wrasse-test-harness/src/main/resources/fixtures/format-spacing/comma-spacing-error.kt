package sample

fun add(a: Int , b: Int): Int {
    val (x , y) = a to b
    return x + y
}

// expect-error 1:1 format "File is not wrasse-formatted"
