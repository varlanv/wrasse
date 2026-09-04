package sample

interface Sup1

interface Sup2

class Baz(a: Int) : Sup1, Sup2

// expect-clean
