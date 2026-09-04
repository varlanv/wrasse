package sample

interface Sup1

interface Sup2

class Point(
    val x: Int,
    val y: Int,
    val z: Int,
)

class Small(a: Int, b: Int)

class Foo(a: Int) : Sup1

class Baz(a: Int) : Sup1, Sup2

class Qux(
    a: Int,
    b: Int,
    c: Int,
) : Sup1,
    Sup2

// expect-clean
