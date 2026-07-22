package sample

fun foo(x: Int) {
    val s = x.toString() + "!"
}

// expect-error 4:13 string-concatenation "String concatenation via '+'; prefer a string template"
