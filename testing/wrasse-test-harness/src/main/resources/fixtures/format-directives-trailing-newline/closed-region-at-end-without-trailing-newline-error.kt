package sample

fun first() = 1
// wrasse fmt:off
fun second( )   =   2
// wrasse fmt:on

// expect-error 1:1 trailing-newline "File must end with a newline"
// expect-error 1:1 format "File is not wrasse-formatted"
