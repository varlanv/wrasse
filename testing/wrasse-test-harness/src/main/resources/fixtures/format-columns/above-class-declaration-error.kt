package sample

// wrasse columns:2
class Bag(vararg val items: Int)

// expect-error 3:1 format-columns "'// wrasse columns:2' has no call to lay out: no call with a parenthesized argument list starts on the first line below it that is neither an annotation-only line nor another layout directive (a function, constructor or class declaration is not a call)"
