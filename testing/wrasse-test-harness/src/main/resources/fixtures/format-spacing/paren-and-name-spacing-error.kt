package sample

fun demo (a: Int) {
    if(a > 0) {
        println( a )
    }
    demo (a - 1)
}

// expect-error 1:1 format "File is not wrasse-formatted"
