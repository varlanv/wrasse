package sample

fun foo() {
    var i = 0
    i = i++ + 1
}

// expect-error 5:9 useless-postfix-expression "The result of the postfix expression 'i++' will not be used and is therefore useless"
