package sample

class Box<T>(val value: T)
class Bag<T>(val value: T)

fun <T> assertEqualCollections(actual: Box<T>, expected: Bag<T>): Boolean = actual.value == expected.value
fun <T> assertEqualCollections(expected: Bag<T>, actual: Box<T>): Boolean = actual.value == expected.value

fun demo(): Boolean = assertEqualCollections(Box(1), Bag(1))

// expect-clean
