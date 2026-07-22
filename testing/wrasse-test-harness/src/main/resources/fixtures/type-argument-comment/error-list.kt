package sample

class Pair2<A, B>

fun sample(p: Pair2</* raw */ Int, String>) {}

// expect-error 5:21 type-argument-comment "A comment in a type argument list is only allowed when placed on a separate line"
