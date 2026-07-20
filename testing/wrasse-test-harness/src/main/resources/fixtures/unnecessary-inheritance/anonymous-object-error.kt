package sample

fun f(): Any {
    return object : Any() {}
}

// expect-error 4:21 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
