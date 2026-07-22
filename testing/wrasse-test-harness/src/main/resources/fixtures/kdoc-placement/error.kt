package sample

val foo1 = 1

val foo2 /** doc */ = 2

// expect-error 5:10 kdoc-placement "A KDoc is allowed only at the start of a 'property'"
