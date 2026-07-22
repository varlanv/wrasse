package sample

class Box<T>

@Suppress("no-semicolons")
fun sample(box: Box<out /* raw */ Any>) {}

// expect-error 6:25 type-argument-comment "A comment inside or on the same line after a type projection is not allowed. Place it on a separate line above."
