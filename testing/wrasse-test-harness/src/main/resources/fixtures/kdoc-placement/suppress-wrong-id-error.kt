package sample

@Suppress("no-semicolons")
val foo2 /** doc */ = 2

// expect-error 4:10 kdoc-placement "A KDoc is allowed only at the start of a 'property'"
