package sample

fun foo(x: Int) {
    val s = "value: " + x
}

// expect-error 4:13 string-concatenation "String concatenation via '+'; prefer a string template"
