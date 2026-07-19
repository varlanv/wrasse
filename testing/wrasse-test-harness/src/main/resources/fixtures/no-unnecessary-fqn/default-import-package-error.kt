package sample

fun f(): kotlin.Unit = Unit

// expect-error 3:10 no-unnecessary-fqn "Unnecessary fully qualified name"