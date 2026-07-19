package sample

class Foo
class Bar

operator fun Foo.invoke(): Bar = Bar()
operator fun Bar.invoke(f: () -> Unit) {}

fun main() {
    val foo = Foo()
    foo()() { }
}

// expect-clean
