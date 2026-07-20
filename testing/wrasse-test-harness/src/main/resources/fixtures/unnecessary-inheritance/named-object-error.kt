package sample

object Foo : Any() {
    val bar = 1
}

// expect-error 3:14 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
