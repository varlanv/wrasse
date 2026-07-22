package sample

fun f(a: Int): String {
    if (a == 1) return "1"
    else if (a == 2) return "2"
    else if (a == 3) return "3"
    else if (a == 4) return "4"
    else if (a == 5) return "5"
    else if (a == 6) return "6"
    else if (a == 7) return "7"
    else if (a == 8) return "8"
    else if (a == 9) return "9"
    else if (a == 10) return "10"
    else if (a == 11) return "11"
    else if (a == 12) return "12"
    else if (a == 13) return "13"
    else if (a == 14) return "14"
    else return "other"
}

// expect-error 3:5 cyclomatic-complexity "Function 'f' has a cyclomatic complexity of 15; the maximum allowed is 14"
