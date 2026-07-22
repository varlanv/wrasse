package sample

@Suppress("no-such-rule")
fun foo() {
    var i = 0
    i = i++
}

// expect-error 6:9 useless-postfix-expression "The result of the postfix expression 'i++' will not be used and is therefore useless"
