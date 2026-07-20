package sample

fun add(a: Int , b: Int,c: Int): Int {
    val (x , y) = a to b
    return x + y + c
}

// expect-error 1:1 format "File is not wrasse-formatted"
