package sample

val a = 1;
//wrasse fmt:off
val b = 2;
// wrasse fmt:on
val c = 3;

// expect-error 3:10 no-semicolons "Unnecessary semicolon"
// expect-error 5:10 no-semicolons "Unnecessary semicolon"
// expect-error 7:10 no-semicolons "Unnecessary semicolon"
