package sample

fun foo() {
    var i = 0
    i = 1 + i++
}

// expect-error 5:13 useless-postfix-expression "The result of the postfix expression 'i++' will not be used and is therefore useless"
