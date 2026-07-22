package sample

class Foo
class Bar

fun Foo.process(x: Int): String = "foo"
fun Bar.process(x: Int): String = "bar"

// expect-clean
