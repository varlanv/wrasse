package sample

interface Sup1

interface Sup2

class Foo(a: Int) : Sup1, /* keep */ Sup2

class Bar