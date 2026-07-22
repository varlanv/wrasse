package sample

fun foo(i: Int?) {
    var x = i
    x = x!!
}

// expect-clean
