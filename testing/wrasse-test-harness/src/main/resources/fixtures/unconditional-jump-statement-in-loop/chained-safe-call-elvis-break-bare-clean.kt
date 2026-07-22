package sample

class Box<T>(private val value: T?) {
    fun poll(): T? = value
}

fun foo(box: Box<Int>) {
    while (true) box.poll()?.let { println(it) } ?: break
}

// expect-clean
