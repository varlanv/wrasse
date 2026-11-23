package sample

fun first( )   =   1
val   x  =  2 // wrasse fmt:off
fun second( )   =   2
// wrasse fmt:on
fun third( )   =   3

// expect-error 1:1 format "File is not wrasse-formatted"
