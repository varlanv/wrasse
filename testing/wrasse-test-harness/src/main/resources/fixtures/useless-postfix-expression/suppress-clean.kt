package sample

@Suppress("useless-postfix-expression")
fun foo() {
    var i = 0
    i = i++
}

// expect-clean
