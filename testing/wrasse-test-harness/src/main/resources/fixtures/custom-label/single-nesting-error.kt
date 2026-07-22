package sample

fun foo(items: List<Int>) {
    qq@ for (item in items) {
        break@qq
    }
}

// expect-error 5:14 custom-label "Custom label @qq is unnecessary; there is no nested loop or forEach for it to disambiguate"
