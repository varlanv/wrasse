package sample

class Foo :
    Any() {
    val x = 1
}

// expect-error 4:5 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
