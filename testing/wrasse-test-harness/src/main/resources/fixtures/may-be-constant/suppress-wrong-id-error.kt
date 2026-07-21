@file:Suppress("no-semicolons")

package sample

val greeting = "hello"

// expect-error 5:5 may-be-constant "Property 'greeting' can be a 'const val'"
