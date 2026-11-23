package sample

val total  =  1 +
    // wrasse fmt:off
    2   +   3 +
    // wrasse fmt:on
    4

// expect-error 1:1 format "File is not wrasse-formatted"
