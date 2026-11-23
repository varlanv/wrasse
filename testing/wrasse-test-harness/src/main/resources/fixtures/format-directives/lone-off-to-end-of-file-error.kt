package sample

fun first( )   =   1

// wrasse fmt:off
fun second( )   =   2;
val   x  =  3

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 6:22 no-semicolons "Unnecessary semicolon"
