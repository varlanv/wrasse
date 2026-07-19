package sample

fun fooBar(foo: () -> String): (() -> String) -> String = { bar -> foo().plus("  ").plus(bar()) }

val result = fooBar { "Hello" }() { "world" }

// expect-clean
