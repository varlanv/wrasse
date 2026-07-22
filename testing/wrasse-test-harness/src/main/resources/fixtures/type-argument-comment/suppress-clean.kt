package sample

class Box<T>

@Suppress("type-argument-comment")
fun sample(box: Box<out /* raw */ Any>) {}

// expect-clean
