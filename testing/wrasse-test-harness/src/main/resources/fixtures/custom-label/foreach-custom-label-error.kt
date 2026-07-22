package sample

fun foo(items: List<Int>) {
    items.forEach qq@{
        return@qq
    }
}

// expect-error 5:15 custom-label "Custom label @qq is unnecessary; there is no nested loop or forEach for it to disambiguate"
