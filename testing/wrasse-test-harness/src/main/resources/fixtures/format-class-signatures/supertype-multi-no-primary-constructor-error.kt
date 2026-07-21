package sample

interface Sup1

interface Sup2

class Foo : Sup1, Sup2

// expect-error 1:1 format "File is not wrasse-formatted"
