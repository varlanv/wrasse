package sample

interface Sup

class Bar(a: Int, b: Int, c: Int) : Sup

// expect-error 1:1 format "File is not wrasse-formatted"
