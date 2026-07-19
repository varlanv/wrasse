package sample

@Suppress("no-semicolons")
class Suppressed {
    val a = 1;
}

class Other {
    val b = 2;
}

// expect-error 9:14 no-semicolons "Unnecessary semicolon"
