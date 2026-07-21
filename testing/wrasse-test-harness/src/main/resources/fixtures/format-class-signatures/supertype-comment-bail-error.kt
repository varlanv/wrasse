package sample

interface Sup1

interface Sup2

class Foo(a: Int) : Sup1, /* keep */ Sup2


class Bar

// expect-error 1:1 format "File is not wrasse-formatted"
