package sample

@Target(AnnotationTarget.TYPE)
annotation class Foo

val myFun1: @Foo () -> Unit = {}
val typeParameter1: (@Foo () -> Unit) -> Unit = { it() }

// expect-clean
