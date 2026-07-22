package sample

/** Doc for Foo */
class Foo

/** Doc for prop */
val prop: Int = 1

/** Doc for doStuff */
fun doStuff() {}

/** Doc for MyObject */
object MyObject

/** Doc for MyAlias */
typealias MyAlias = Foo

// expect-clean
