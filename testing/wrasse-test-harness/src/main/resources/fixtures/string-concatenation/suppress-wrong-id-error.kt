package sample

@Suppress("no-such-rule")
fun foo(x: Int) {
    val s = "value: " + x
}

// expect-error 5:13 string-concatenation "String concatenation via '+'; prefer a string template"
