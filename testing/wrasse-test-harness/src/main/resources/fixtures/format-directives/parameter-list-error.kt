package sample

fun configure(
    first : Int,
    // wrasse fmt:off
    second:   Int,
    third : Int,
    // wrasse fmt:on
    fourth : Int,
) = first

// expect-error 1:1 format "File is not wrasse-formatted"
