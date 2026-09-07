package sample

class Foo {
    val prop: Int = 0
        get() = field
}

val greeting: String = computeGreeting()
    get() = field

fun computeGreeting(): String = "hi"

// expect-error 5:9 trivial-accessors "Trivial accessor"
// expect-error 9:5 trivial-accessors "Trivial accessor"
