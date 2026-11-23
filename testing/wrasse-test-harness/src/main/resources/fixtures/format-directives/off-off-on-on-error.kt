package sample

fun first( )   =   1
// wrasse fmt:off
fun second( )   =   2
// wrasse fmt:off
fun third( )   =   3
// wrasse fmt:on
fun fourth( )   =   4
// wrasse fmt:on
fun fifth( )   =   5

// expect-error 1:1 format "File is not wrasse-formatted"
