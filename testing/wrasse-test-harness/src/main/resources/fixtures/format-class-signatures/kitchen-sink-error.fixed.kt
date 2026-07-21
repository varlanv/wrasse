package sample

interface Sup1

interface Sup2

interface VeryLongSuperTypeName

class Point(
    val x: Int,
    val y: Int,
    val z: Int,
)

class Foo(a: Int) :
    VeryLongSuperTypeName

class Baz(a: Int) :
    Sup1,
    Sup2

class Qux(
    a: Int,
    b: Int,
    c: Int,
) : Sup1,
    Sup2