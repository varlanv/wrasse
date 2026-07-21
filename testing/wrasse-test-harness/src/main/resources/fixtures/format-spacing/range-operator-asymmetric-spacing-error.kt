package sample

fun demo(): List<Int> {
    val a = (1.. 5).toList()
    val b = (1 ..5).toList()
    return a + b
}

// expect-error 1:1 format "File is not wrasse-formatted"
