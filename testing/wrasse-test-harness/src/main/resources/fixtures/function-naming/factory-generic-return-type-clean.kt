package sample

class Generics<T>(val value: T)

fun <T> Generics(action: () -> T): Generics<T> = Generics(action())

// expect-clean
