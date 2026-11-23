package sample

fun  first( ) {
    val  a  =  1
    // wrasse fmt:off
    val  b  =  2
}

fun  second( ) {
    val  c  =  3
    // wrasse fmt:on
    val  d  =  4
}

// expect-error 1:1 format "File is not wrasse-formatted"
