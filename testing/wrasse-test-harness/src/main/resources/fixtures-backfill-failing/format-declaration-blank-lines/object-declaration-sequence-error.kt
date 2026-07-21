package sample

class Foo
data class Bar(val v: Int)
interface Baz
object Qux

// expect-error 1:1 format "File is not wrasse-formatted"
