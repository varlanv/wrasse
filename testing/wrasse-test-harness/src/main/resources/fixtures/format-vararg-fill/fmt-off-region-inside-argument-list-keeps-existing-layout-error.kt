package sample

val a = intArrayOf(
    10, 20, 30, 40,
    // wrasse fmt:off
    50,   60,   70,
    // wrasse fmt:on
    80, 90)

// expect-error 1:1 format "File is not wrasse-formatted"
