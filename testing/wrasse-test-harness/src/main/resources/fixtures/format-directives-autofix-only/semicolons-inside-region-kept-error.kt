package sample

fun main() {
    val a = 1;
    // wrasse fmt:off
    val b = 2;
    val c = 3;
    // wrasse fmt:on
    val d = 4;
}

// expect-error 4:14 no-semicolons "Unnecessary semicolon"
// expect-error 6:14 no-semicolons "Unnecessary semicolon"
// expect-error 7:14 no-semicolons "Unnecessary semicolon"
// expect-error 9:14 no-semicolons "Unnecessary semicolon"
