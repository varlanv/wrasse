package sample

interface Sup1

interface Sup2

class Qux(a: Int, b: Int, c: Int) : Sup1, Sup2

// expect-error 1:1 format "File is not wrasse-formatted"
