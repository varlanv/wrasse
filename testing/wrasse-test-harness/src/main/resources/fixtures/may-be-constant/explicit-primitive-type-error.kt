package sample

val greeting: String = "hello"

// expect-error 3:5 may-be-constant "Property 'greeting' can be a 'const val'"
