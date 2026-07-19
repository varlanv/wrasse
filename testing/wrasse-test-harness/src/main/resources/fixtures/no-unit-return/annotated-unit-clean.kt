package sample

@Target(AnnotationTarget.TYPE)
annotation class Ann

fun foo(): @Ann Unit {}

// expect-clean
