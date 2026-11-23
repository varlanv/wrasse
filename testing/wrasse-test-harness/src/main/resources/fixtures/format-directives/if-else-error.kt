package sample

fun check(x: Int): Int {
    if (x   >   1) {
        return   1
    }
    // wrasse fmt:off
    else   {
        return   2
    }
    // wrasse fmt:on
    return   3
}

// expect-error 1:1 format "File is not wrasse-formatted"
